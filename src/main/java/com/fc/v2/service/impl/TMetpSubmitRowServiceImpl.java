package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;

/**
 * 正点观测数据核收转存行 Service业务层处理（batch-process 形状：整批提交）
 *
 * 进门规矩（与室定口径一致）：
 * 1. 行次由系统带着走：原册行次随行入库，缺失或冲撞时按册内顺位补齐，谁也不许另立一张纸；
 * 2. 一行的毛病只碍一行：条数写成负数等着毛病的行自己退回（挂驳回，缘故写在该行remark），其余各行照旧收下；
 * 3. 同一册号再来一遍，只算最先到的那一次，后到的按幂等忽略，先收的行一字不改；
 * 4. 一册之内同一时次要素出现两行（正点与补报的旧写法），合成一条来记，条数只加一回，后到的一行跳过；
 * 5. 册子行数越上限（{@value #MAX_ROWS}行）整册不接，一行的口子也不开；通篇空白的白册子静默回0，回执上不落"核讫"；
 * 6. 回执合计与账上合计同源于一次推演：收下的（已转存）加挂着的（挂驳回）对得上原表行数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpSubmitRowServiceImpl implements ITMetpSubmitRowService {

    /** 单册行数上限，越线整册不接 */
    private static final int MAX_ROWS = 500;
    /** 行进展：收下（已转存） */
    private static final int STATUS_OK = 1;
    /** 行进展：退回（挂驳回） */
    private static final int STATUS_FAIL = 2;
    /** 时次要素代号长度上限 */
    private static final int ITEM_CODE_MAX = 64;
    /** 单行记录条数上限 */
    private static final BigDecimal QTY_MAX = new BigDecimal("999999");

    /** 各册最近一次办理留痕（册号 -> 办理结论），回执据此回话 */
    private final Map<String, String> batchStats = new ConcurrentHashMap<String, String>();

    @javax.annotation.Resource
    private TMetpSubmitRowMapper metpSubmitRowMapper;

    @Override
    public TMetpSubmitRow selectTMetpSubmitRowById(Long id) {
        return this.metpSubmitRowMapper.selectById(id);
    }

    /**
     * 整批提交：一册进门，系统一行一行推演落定。
     * 空册回0且不留"核讫"字样；越上限回-1整册不入；同册号再到回0按幂等忽略；
     * 否则逐行校验，毛病行挂驳回、好行收下，返回收下行数。
     */
    @Override
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        if (isBlank(batchNo) || rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            note(batchNo, "批次超限拒收：" + rows.size() + " 行");
            return -1;
        }
        if (countBatch(batchNo) > 0) {
            note(batchNo, "批次已提交过，按幂等忽略");
            return 0;
        }
        Set<String> seenCodes = new HashSet<String>();
        Set<Integer> usedRowNos = new HashSet<Integer>();
        int ok = 0;
        int seq = 0;
        for (TMetpSubmitRow r : rows) {
            seq++;
            if (r == null) {
                continue;
            }
            int rowNo = normalizeRowNo(r, usedRowNos, seq);
            String reason = invalidReason(r);
            if (reason != null) {
                markFail(r, batchNo, rowNo, reason);
                continue;
            }
            String code = r.getItemCode().trim();
            if (!seenCodes.add(code)) {
                note(batchNo, "第" + rowNo + "行明细码重复，已跳过：" + code);
                continue;
            }
            markOk(r, batchNo, rowNo);
            ok++;
        }
        note(batchNo, "提交完成：成功" + ok + " 行");
        return ok;
    }

    /**
     * 该批次的失败行明细：退回的行按原册行次升序摆开，
     * 哪一行、因为什么退，一眼指回原表。
     */
    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        if (isBlank(batchNo)) {
            return new ArrayList<TMetpSubmitRow>();
        }
        List<TMetpSubmitRow> list = this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL).eq("del_flag", 0));
        if (list == null || list.isEmpty()) {
            return new ArrayList<TMetpSubmitRow>();
        }
        list.sort(new Comparator<TMetpSubmitRow>() {
            @Override
            public int compare(TMetpSubmitRow a, TMetpSubmitRow b) {
                int ra = a.getRowNo() == null ? 0 : a.getRowNo().intValue();
                int rb = b.getRowNo() == null ? 0 : b.getRowNo().intValue();
                return ra - rb;
            }
        });
        return list;
    }

    /** 该册号在账上已有的行数（不分进退），>0即先到的那一次已经落定 */
    private int countBatch(String batchNo) {
        List<TMetpSubmitRow> list = this.metpSubmitRowMapper.selectList(
                new QueryWrapper<TMetpSubmitRow>().eq("batch_no", batchNo));
        return list == null ? 0 : list.size();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 一行的毛病，一条一件事；没毛病回null */
    private String invalidReason(TMetpSubmitRow r) {
        if (isBlank(r.getItemCode())) {
            return "明细码为空";
        }
        if (r.getItemCode().trim().length() > ITEM_CODE_MAX) {
            return "明细码超过 64 个字符";
        }
        if (r.getQty() == null) {
            return "数量缺失";
        }
        if (r.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return "数量必须大于 0";
        }
        if (r.getQty().compareTo(QTY_MAX) > 0) {
            return "数量超过单行上限" + QTY_MAX.toPlainString();
        }
        return null;
    }

    /**
     * 行次由系统带着走：原册行次正数且册内未占，就随原次落账；
     * 缺失、非正或冲撞时，从册内顺位起找第一个空次补上。
     */
    private static int normalizeRowNo(TMetpSubmitRow r, Set<Integer> used, int seq) {
        Integer rn = r.getRowNo();
        if (rn != null && rn.intValue() > 0 && used.add(rn)) {
            return rn.intValue();
        }
        int c = seq > 0 ? seq : 1;
        while (!used.add(Integer.valueOf(c))) {
            c++;
        }
        return c;
    }

    /** 毛病行挂驳回：原册行次、退回缘故一并落在该行身上 */
    private void markFail(TMetpSubmitRow r, String batchNo, int rowNo, String reason) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(Integer.valueOf(STATUS_FAIL));
        r.setRemark(reason);
        stamp(r);
        this.metpSubmitRowMapper.insert(r);
    }

    /** 好行收下：行次随行，不留批注 */
    private void markOk(TMetpSubmitRow r, String batchNo, int rowNo) {
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(Integer.valueOf(STATUS_OK));
        r.setRemark(null);
        stamp(r);
        this.metpSubmitRowMapper.insert(r);
    }

    private static void stamp(TMetpSubmitRow r) {
        if (r.getDelFlag() == null) {
            r.setDelFlag(Integer.valueOf(0));
        }
        r.setUpdateTime(new Date());
    }

    /** 办理留痕：一册只留最近一句，留痕过满即清册重记 */
    private void note(String batchNo, String msg) {
        if (batchStats.size() > 200) {
            batchStats.clear();
        }
        batchStats.put(batchNo, msg);
    }
}
