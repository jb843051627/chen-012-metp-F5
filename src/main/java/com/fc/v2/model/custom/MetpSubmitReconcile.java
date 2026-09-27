package com.fc.v2.model.custom;

import java.io.Serializable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 正点观测数据对账单：一趟 GROUP BY 同源算出各堆，
 * 挂着的（待销+挂驳回）加上转存的等于在账行数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@ApiModel(value = "MetpSubmitReconcile", description = "正点观测数据对账单")
public class MetpSubmitReconcile implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 册号，为空表示核全账 */
    @ApiModelProperty(value = "册号，空为全账")
    private String batchNo;

    /** 待销（收下在账，候着归档） */
    @ApiModelProperty(value = "待销行数")
    private Integer pending;

    /** 挂驳回（退回） */
    @ApiModelProperty(value = "挂驳回行数")
    private Integer rejected;

    /** 已转存（挪进历史库） */
    @ApiModelProperty(value = "已转存行数")
    private Integer archived;

    /** 挂着的 = 待销 + 挂驳回 */
    @ApiModelProperty(value = "挂着的行数")
    private Integer hanging;

    /** 在账行数（含串门的异常状态行） */
    @ApiModelProperty(value = "在账行数")
    private Integer total;

    /** 等式成不成：挂着的 + 转存的 == 在账行数 */
    @ApiModelProperty(value = "等式成不成")
    private Boolean balanced;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getPending() {
        return pending;
    }

    public void setPending(Integer pending) {
        this.pending = pending;
    }

    public Integer getRejected() {
        return rejected;
    }

    public void setRejected(Integer rejected) {
        this.rejected = rejected;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
    }

    public Integer getHanging() {
        return hanging;
    }

    public void setHanging(Integer hanging) {
        this.hanging = hanging;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Boolean getBalanced() {
        return balanced;
    }

    public void setBalanced(Boolean balanced) {
        this.balanced = balanced;
    }
}
