package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.model.custom.MetpSubmitBatch;
import com.fc.v2.model.custom.MetpSubmitReceipt;
import com.fc.v2.model.custom.MetpSubmitReconcile;
import com.fc.v2.service.ITMetpSubmitRowService;
import com.fc.v2.util.StringUtils;

import javax.annotation.Resource;

/**
 * 正点观测数据核收转存行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpSubmitRowServiceImpl implements ITMetpSubmitRowService {

    @Resource
    private TMetpSubmitRowMapper metpSubmitRowMapper;

    @Resource
    private PlatformTransactionManager transactionManager;

    /** 事务模板每用每取，免得把连接早早地拽在手里 */
    private TransactionTemplate txTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public TMetpSubmitRow selectTMetpSubmitRowById(Long id) {
        return this.metpSubmitRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        MetpSubmitReceipt receipt = submitBatchReceipt(batchNo, rows);
        if (MetpSubmitReceipt.RESULT_OVERSIZE.equals(receipt.getResult())) {
            return -1;
        }
        return receipt.getAccepted() == null ? 0 : receipt.getAccepted().intValue();
    }

    /**
     * 一册一锁一事务：锁在事务外头，先落锁再开账，同事务内查重册才作数；
     * 册号不同各走各的锁，两个县同递互不排队。
     */
    private MetpSubmitReceipt submitBatchReceipt(String batchNo, List<TMetpSubmitRow> rows) {
        String lockNo = batchNo == null ? "" : batchNo.trim();
        // 册号还没定下来的（空册、缺号），先不锁，进推演里自然被挡
        if (lockNo.isEmpty() && rows != null) {
            for (TMetpSubmitRow row : rows) {
                if (row != null && row.getBatchNo() != null && !row.getBatchNo().trim().isEmpty()) {
                    lockNo = row.getBatchNo().trim();
                    break;
                }
            }
        }
        synchronized (("METP_SUBMIT_" + lockNo).intern()) {
            return txTemplate().execute(status -> adjudicate(batchNo, rows));
        }
    }

    @Override
    public int submitBatch(List<TMetpSubmitRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 按册号归堆（先见先排），一册一册过堂；各册互不相干，先后分开办与一起办账上一般模样
        Map<String, List<TMetpSubmitRow>> groups = new LinkedHashMap<String, List<TMetpSubmitRow>>();
        for (TMetpSubmitRow row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBatchNo())) {
                continue; // 没戴册号的行立不了账，跳过
            }
            String no = row.getBatchNo().trim();
            List<TMetpSubmitRow> group = groups.get(no);
            if (group == null) {
                group = new ArrayList<TMetpSubmitRow>();
                groups.put(no, group);
            }
            group.add(row);
        }
        int accepted = 0;
        for (Map.Entry<String, List<TMetpSubmitRow>> entry : groups.entrySet()) {
            // 一册一锁一事务，册与册不挤一个账，一册出了岔子不拖别的册下水
            int n = this.submitBatch(entry.getKey(), entry.getValue());
            if (n > 0) {
                accepted += n;
            }
        }
        return accepted;
    }

    @Override
    public MetpSubmitReceipt submitBatch(MetpSubmitBatch bill) {
        if (bill == null) {
            return adjudicate(null, null);
        }
        return submitBatchReceipt(bill.getBatchNo(), bill.getRows());
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_REJECTED)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TMetpSubmitRow> selectTMetpSubmitRowList(String batchNo, String itemCode, Integer status) {
        QueryWrapper<TMetpSubmitRow> queryWrapper = new QueryWrapper<TMetpSubmitRow>();
        queryWrapper.eq("del_flag", 0);
        if (StringUtils.isNotEmpty(batchNo)) {
            queryWrapper.eq("batch_no", batchNo.trim());
        }
        if (StringUtils.isNotEmpty(itemCode)) {
            queryWrapper.eq("item_code", itemCode.trim());
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        queryWrapper.orderByAsc("batch_no", "row_no");
        return this.metpSubmitRowMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveDueRows() {
        return this.archiveDueRows(new Date());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveDueRows(Date asOf) {
        Date now = asOf != null ? asOf : new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_YEAR, -ARCHIVE_DAYS);
        // 收下的行满九十天挪进历史库：0待销 → 1已转存，退回行不动
        return this.metpSubmitRowMapper.update(null, new UpdateWrapper<TMetpSubmitRow>()
                .set("status", STATUS_ARCHIVED)
                .set("update_time", now)
                .eq("status", STATUS_PENDING)
                .eq("del_flag", 0)
                .le("create_time", calendar.getTime()));
    }

    @Override
    public MetpSubmitReconcile reconcile(String batchNo) {
        // 同源于一次计算：一趟 GROUP BY 出各堆，挂着的加转存的等于在账行数
        QueryWrapper<TMetpSubmitRow> queryWrapper = new QueryWrapper<TMetpSubmitRow>()
                .select("status", "COUNT(*) AS cnt")
                .eq("del_flag", 0)
                .groupBy("status");
        if (StringUtils.isNotEmpty(batchNo)) {
            queryWrapper.eq("batch_no", batchNo.trim());
        }
        List<Map<String, Object>> groups = this.metpSubmitRowMapper.selectMaps(queryWrapper);
        int pending = 0;
        int rejected = 0;
        int archived = 0;
        int total = 0;
        for (Map<String, Object> group : groups) {
            // 各库对别名字段的大小写回话不一（MySQL原样、H2/Oracle大写），取值时大小写不敏感
            Number cnt = (Number) mapValueIgnoreCase(group, "cnt");
            int n = cnt == null ? 0 : cnt.intValue();
            total += n;
            Number status = (Number) mapValueIgnoreCase(group, "status");
            if (status == null) {
                continue;
            }
            switch (status.intValue()) {
                case STATUS_PENDING:
                    pending = n;
                    break;
                case STATUS_ARCHIVED:
                    archived = n;
                    break;
                case STATUS_REJECTED:
                    rejected = n;
                    break;
                default:
                    // 串了门的异常状态：只计入在账行数，等式自然凑不齐，对账时现形
                    break;
            }
        }
        MetpSubmitReconcile reconcile = new MetpSubmitReconcile();
        reconcile.setBatchNo(StringUtils.isNotEmpty(batchNo) ? batchNo.trim() : null);
        reconcile.setPending(pending);
        reconcile.setRejected(rejected);
        reconcile.setArchived(archived);
        reconcile.setHanging(pending + rejected);
        reconcile.setTotal(total);
        reconcile.setBalanced(pending + rejected + archived == total);
        return reconcile;
    }

    // ==================== 册内推演（服务层自家差事） ====================

    /**
     * 一册过堂：先挡册（白册、缺号、重册、超行），再逐行推演落账。
     * 回执上的合计与账上的合计同源于这一趟计算。
     */
    private MetpSubmitReceipt adjudicate(String batchNo, List<TMetpSubmitRow> rows) {
        MetpSubmitReceipt receipt = new MetpSubmitReceipt();
        receipt.setLines(new ArrayList<MetpSubmitReceipt.Line>());

        // 白册子：通篇无行，回执上不出"核讫"
        if (rows == null || rows.isEmpty()) {
            receipt.setBatchNo(trimToNull(batchNo));
            receipt.setResult(MetpSubmitReceipt.RESULT_BLANK);
            receipt.setNote("通篇无行，不予核收");
            receipt.setSubmitted(0);
            zeroTotals(receipt);
            return receipt;
        }
        receipt.setSubmitted(rows.size());

        // 册号：单上没带就取行上戴的
        String no = trimToNull(batchNo);
        if (no == null) {
            no = firstBatchNo(rows);
        }
        if (no == null) {
            receipt.setResult(MetpSubmitReceipt.RESULT_NO_BATCH);
            receipt.setNote("册子没戴册号，立不了账");
            zeroTotals(receipt);
            return receipt;
        }
        receipt.setBatchNo(no);

        // 重册：同一册号只算最先到的一次，不改先收的行，退回去的缘故也不补第二句
        Integer dup = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (dup != null && dup.intValue() > 0) {
            receipt.setResult(MetpSubmitReceipt.RESULT_DUPLICATE);
            receipt.setNote("该册号先到的一回已在账，本次不重入、不改账；真要更正数目，请另开差异清单");
            zeroTotals(receipt);
            return receipt;
        }

        // 超行：越上限整册不接，一行的口子也不开
        if (rows.size() > MAX_ROWS) {
            receipt.setResult(MetpSubmitReceipt.RESULT_OVERSIZE);
            receipt.setNote("本册 " + rows.size() + " 行，越上限 " + MAX_ROWS + " 行，整册不接");
            zeroTotals(receipt);
            return receipt;
        }

        // 逐行过堂：行次由系统按原册顺序带着走，谁也不许另立一张纸
        List<TMetpSubmitRow> rejectedRows = new ArrayList<TMetpSubmitRow>();
        Map<String, MergeBucket> buckets = new LinkedHashMap<String, MergeBucket>();
        int accepted = 0;
        int rowNo = 0;
        for (TMetpSubmitRow row : rows) {
            rowNo++;
            MetpSubmitReceipt.Line line = new MetpSubmitReceipt.Line();
            line.setRowNo(rowNo);
            receipt.getLines().add(line);

            String reason = rejectReason(row);
            if (reason != null) {
                // 那一行自己退回去，缘故写在行身上，其余各行不受牵连
                TMetpSubmitRow rejectedRow = new TMetpSubmitRow();
                rejectedRow.setBatchNo(no);
                rejectedRow.setRowNo(rowNo);
                rejectedRow.setItemCode(row == null ? null : trimToNull(row.getItemCode()));
                rejectedRow.setQty(row == null ? null : row.getQty());
                rejectedRow.setStatus(STATUS_REJECTED);
                rejectedRow.setDelFlag(0);
                rejectedRow.setRemark(reason);
                rejectedRows.add(rejectedRow);

                line.setItemCode(rejectedRow.getItemCode());
                line.setQty(rejectedRow.getQty());
                line.setDisposition(MetpSubmitReceipt.LINE_REJECTED);
                line.setReason(reason);
                continue;
            }

            String itemCode = row.getItemCode().trim();
            accepted++;
            MergeBucket bucket = buckets.get(itemCode);
            if (bucket == null) {
                bucket = new MergeBucket();
                bucket.firstRowNo = rowNo;
                bucket.qtySum = row.getQty();
                bucket.rowNos.add(Integer.valueOf(rowNo));
                buckets.put(itemCode, bucket);
                line.setDisposition(MetpSubmitReceipt.LINE_ACCEPTED);
                line.setLedgerRowNo(rowNo);
            } else {
                // 正点与补报的旧写法：合成一条来记，条数只加一回
                bucket.qtySum = bucket.qtySum.add(row.getQty());
                bucket.rowNos.add(Integer.valueOf(rowNo));
                line.setDisposition(MetpSubmitReceipt.LINE_MERGED);
                line.setLedgerRowNo(bucket.firstRowNo);
            }
            line.setItemCode(itemCode);
            line.setQty(row.getQty());
        }

        // 落账：退回行与收下行（并行后）一趟写入
        for (TMetpSubmitRow rejectedRow : rejectedRows) {
            this.metpSubmitRowMapper.insert(rejectedRow);
        }
        for (Map.Entry<String, MergeBucket> entry : buckets.entrySet()) {
            MergeBucket bucket = entry.getValue();
            TMetpSubmitRow acceptedRow = new TMetpSubmitRow();
            acceptedRow.setBatchNo(no);
            acceptedRow.setRowNo(bucket.firstRowNo);
            acceptedRow.setItemCode(entry.getKey());
            acceptedRow.setQty(bucket.qtySum);
            acceptedRow.setStatus(STATUS_PENDING);
            acceptedRow.setDelFlag(0);
            acceptedRow.setRemark(mergeRemark(bucket.rowNos));
            this.metpSubmitRowMapper.insert(acceptedRow);
        }

        // 回执合计与账上合计同源于上面这一趟推演
        receipt.setAccepted(accepted);
        receipt.setRejected(rejectedRows.size());
        receipt.setLedgerAccepted(buckets.size());
        receipt.setLedgerRejected(rejectedRows.size());
        receipt.setLedgerTotal(buckets.size() + rejectedRows.size());
        receipt.setMerged(accepted - buckets.size());
        if (rejectedRows.isEmpty()) {
            receipt.setResult(MetpSubmitReceipt.RESULT_VERIFIED);
        } else if (accepted == 0) {
            receipt.setResult(MetpSubmitReceipt.RESULT_ALL_REJECTED);
            receipt.setNote("全册 " + rejectedRows.size() + " 行尽数退回，缘故逐行写明");
        } else {
            receipt.setResult(MetpSubmitReceipt.RESULT_PARTIAL);
            receipt.setNote("收下 " + accepted + " 行，退回 " + rejectedRows.size() + " 行，缘故逐行写明");
        }
        return receipt;
    }

    /** 回执各档合计归零（被挡下的册：白册、缺号、重册、超行） */
    private void zeroTotals(MetpSubmitReceipt receipt) {
        receipt.setAccepted(0);
        receipt.setRejected(0);
        receipt.setLedgerAccepted(0);
        receipt.setLedgerRejected(0);
        receipt.setLedgerTotal(0);
        receipt.setMerged(0);
    }

    /** 那一行该不该退：该退给一句缘故，一条一件事；不该退给 null */
    private String rejectReason(TMetpSubmitRow row) {
        if (row == null) {
            return "空行";
        }
        if (trimToNull(row.getItemCode()) == null) {
            return "缺时次要素代号";
        }
        if (row.getQty() == null) {
            return "缺记录条数";
        }
        if (row.getQty().compareTo(BigDecimal.ZERO) < 0) {
            return "记录条数为负";
        }
        return null;
    }

    /** 并行记事：行少列得清就列行次，行多就报行数，免撑破备注栏 */
    private String mergeRemark(List<Integer> rowNos) {
        if (rowNos == null || rowNos.size() <= 1) {
            return null;
        }
        if (rowNos.size() <= 10) {
            StringBuilder sb = new StringBuilder("正点补报合并：原册第");
            for (int i = 0; i < rowNos.size(); i++) {
                if (i > 0) {
                    sb.append('、');
                }
                sb.append(rowNos.get(i));
            }
            sb.append("行");
            return sb.toString();
        }
        return "正点补报合并：共" + rowNos.size() + "行，首为原册第" + rowNos.get(0) + "行";
    }

    private String firstBatchNo(List<TMetpSubmitRow> rows) {
        for (TMetpSubmitRow row : rows) {
            if (row != null && trimToNull(row.getBatchNo()) != null) {
                return row.getBatchNo().trim();
            }
        }
        return null;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 从 selectMaps 的结果里按键名取值，大小写不敏感 */
    private Object mapValueIgnoreCase(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 同一时次要素的并桶：首行立账，余行并入 */
    private static class MergeBucket {
        private int firstRowNo;
        private BigDecimal qtySum;
        private final List<Integer> rowNos = new ArrayList<Integer>();
    }
}
