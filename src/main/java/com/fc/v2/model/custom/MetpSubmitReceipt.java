package com.fc.v2.model.custom;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 正点观测数据核收回执：一册过堂的回话。
 * 逐行写明收下、并入还是退回，退回的指回原表第几行、因为什么退；
 * 合计与账上同源于一次推演，白册子的回执上不出"核讫"。
 *
 * @author fuce
 * @date 2026-09-14
 */
@ApiModel(value = "MetpSubmitReceipt", description = "正点观测数据核收回执")
public class MetpSubmitReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 回执结论：全行收下 */
    public static final String RESULT_VERIFIED = "核讫";
    /** 回执结论：有收有退 */
    public static final String RESULT_PARTIAL = "部分驳回";
    /** 回执结论：全册尽数退回 */
    public static final String RESULT_ALL_REJECTED = "全册驳回";
    /** 回执结论：通篇空白，不予核收（不出"核讫"） */
    public static final String RESULT_BLANK = "白册退回";
    /** 回执结论：同一册号只算最先到的一次 */
    public static final String RESULT_DUPLICATE = "重册不受理";
    /** 回执结论：行数越上限，整册不接 */
    public static final String RESULT_OVERSIZE = "超行整册不接";
    /** 回执结论：册子没戴册号 */
    public static final String RESULT_NO_BATCH = "册号缺失不受理";

    /** 逐行落笔：收下 */
    public static final String LINE_ACCEPTED = "收下";
    /** 逐行落笔：并入首行（正点补报合并） */
    public static final String LINE_MERGED = "并入";
    /** 逐行落笔：退回 */
    public static final String LINE_REJECTED = "退回";

    /** 观测数据报送册号 */
    @ApiModelProperty(value = "观测数据报送册号")
    private String batchNo;

    /** 回执结论（核讫/部分驳回/全册驳回/白册退回/重册不受理/超行整册不接/册号缺失不受理） */
    @ApiModelProperty(value = "回执结论")
    private String result;

    /** 回执附言 */
    @ApiModelProperty(value = "回执附言")
    private String note;

    /** 原表行数 */
    @ApiModelProperty(value = "原表行数")
    private Integer submitted;

    /** 收下的原表行数（正点补报并行的两行都算收下） */
    @ApiModelProperty(value = "收下的原表行数")
    private Integer accepted;

    /** 退回的原表行数 */
    @ApiModelProperty(value = "退回的原表行数")
    private Integer rejected;

    /** 收下立账条数（正点补报并行后） */
    @ApiModelProperty(value = "收下立账条数")
    private Integer ledgerAccepted;

    /** 退回立账条数 */
    @ApiModelProperty(value = "退回立账条数")
    private Integer ledgerRejected;

    /** 本册在账行数（收下立账+退回立账） */
    @ApiModelProperty(value = "本册在账行数")
    private Integer ledgerTotal;

    /** 并掉的行数（收下行数-收下立账条数） */
    @ApiModelProperty(value = "并掉的行数")
    private Integer merged;

    /** 逐行回执，按原册行次升序 */
    @ApiModelProperty(value = "逐行回执")
    private List<Line> lines;

    /**
     * 一行回执：指回原表第几行，落笔收下、并入还是退回，退回写明缘故。
     */
    @ApiModel(value = "MetpSubmitReceiptLine", description = "一行回执")
    public static class Line implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 原册行次（系统按原册顺序带着走） */
        @ApiModelProperty(value = "原册行次")
        private Integer rowNo;

        /** 时次要素代号 */
        @ApiModelProperty(value = "时次要素代号")
        private String itemCode;

        /** 本行填报记录条数 */
        @ApiModelProperty(value = "本行填报记录条数")
        private BigDecimal qty;

        /** 落笔：收下/并入/退回 */
        @ApiModelProperty(value = "落笔")
        private String disposition;

        /** 退回缘故，一条一件事 */
        @ApiModelProperty(value = "退回缘故")
        private String reason;

        /** 落在账上第几行（并入时指向首行行次） */
        @ApiModelProperty(value = "账上行次")
        private Integer ledgerRowNo;

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public BigDecimal getQty() {
            return qty;
        }

        public void setQty(BigDecimal qty) {
            this.qty = qty;
        }

        public String getDisposition() {
            return disposition;
        }

        public void setDisposition(String disposition) {
            this.disposition = disposition;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Integer getLedgerRowNo() {
            return ledgerRowNo;
        }

        public void setLedgerRowNo(Integer ledgerRowNo) {
            this.ledgerRowNo = ledgerRowNo;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getSubmitted() {
        return submitted;
    }

    public void setSubmitted(Integer submitted) {
        this.submitted = submitted;
    }

    public Integer getAccepted() {
        return accepted;
    }

    public void setAccepted(Integer accepted) {
        this.accepted = accepted;
    }

    public Integer getRejected() {
        return rejected;
    }

    public void setRejected(Integer rejected) {
        this.rejected = rejected;
    }

    public Integer getLedgerAccepted() {
        return ledgerAccepted;
    }

    public void setLedgerAccepted(Integer ledgerAccepted) {
        this.ledgerAccepted = ledgerAccepted;
    }

    public Integer getLedgerRejected() {
        return ledgerRejected;
    }

    public void setLedgerRejected(Integer ledgerRejected) {
        this.ledgerRejected = ledgerRejected;
    }

    public Integer getLedgerTotal() {
        return ledgerTotal;
    }

    public void setLedgerTotal(Integer ledgerTotal) {
        this.ledgerTotal = ledgerTotal;
    }

    public Integer getMerged() {
        return merged;
    }

    public void setMerged(Integer merged) {
        this.merged = merged;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }
}
