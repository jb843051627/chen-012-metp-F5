package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.List;

import com.fc.v2.model.auto.TMetpSubmitRow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 正点观测数据整批提交单（门脸唯一认的一号：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@ApiModel(value = "MetpSubmitBatch", description = "正点观测数据整批提交单")
public class MetpSubmitBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 观测数据报送册号 */
    @ApiModelProperty(value = "观测数据报送册号")
    private String batchNo;

    /** 册内各行（行次由系统按顺序带着走，行上不必带；状态由系统推演，行上不许带） */
    @ApiModelProperty(value = "册内各行")
    private List<TMetpSubmitRow> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public List<TMetpSubmitRow> getRows() {
        return rows;
    }

    public void setRows(List<TMetpSubmitRow> rows) {
        this.rows = rows;
    }
}
