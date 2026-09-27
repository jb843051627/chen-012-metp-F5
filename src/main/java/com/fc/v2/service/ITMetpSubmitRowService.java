package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.model.custom.MetpSubmitBatch;
import com.fc.v2.model.custom.MetpSubmitReceipt;
import com.fc.v2.model.custom.MetpSubmitReconcile;

/**
 * 正点观测数据核收转存行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * 室规（今年起）：
 * 1. 进门只认整批提交；一行行拆与账都是本层差事，行次由系统按原册顺序带着走，谁也不许另立一张纸；
 * 2. 记录条数为负的那一行自己退回，其余各行不受牵连照旧收下；缘故写在退回行身上，一条一件事；
 * 3. 同一册号只算最先到的一次，重递不改先收的行、退回去的缘故也不补第二句；更正数目另开差异清单；
 * 4. 一册之内同一时次要素出现两行（正点与补报的旧写法），合成一条来记，条数只加一回；
 * 5. 一册行数越上限整册不接，一行的口子也不开；通篇空白的白册子，回执上不出"核讫"字样；
 * 6. 收下的行满九十天挪进历史库（0待销→1已转存），现行页面不再露脸；册号、时次两条路都能查，
 *    退回的、收下的、进史的各码各堆，互不串门；
 * 7. 回执上的合计与账上的合计同源于一次计算：挂着的（待销+挂驳回）加上转存的等于在账行数。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMetpSubmitRowService {

    /** 行进展：0 待销（收下在账，候着归档） */
    int STATUS_PENDING = 0;
    /** 行进展：1 已转存（挪进历史库） */
    int STATUS_ARCHIVED = 1;
    /** 行进展：2 挂驳回（退回） */
    int STATUS_REJECTED = 2;

    /** 一册行数上限，越线整册不接 */
    int MAX_ROWS = 500;
    /** 收下行在账候归的自然日数，期满挪进历史库 */
    int ARCHIVE_DAYS = 90;

    /** 按主键回查单行 */
    TMetpSubmitRow selectTMetpSubmitRowById(Long id);

    /**
     * 整批提交：逐行校验，合法的入库、非法的记入失败明细（保留原始行号）。
     * 同一批次重复提交不得重复入库；行数超过单批上限整批不入库；空批次不入库。
     *
     * @param batchNo 册号；为空时取行上戴的册号
     * @param rows    册内各行，行次由系统按顺序落定，行上不必带
     * @return 收下的原表行数（正点补报并行的两行都算收下，账上只立一条）；
     *         空批次、重册、缺册号返回 0；行数越上限返回 -1 且整批不入库
     */
    int submitBatch(String batchNo, List<TMetpSubmitRow> rows);

    /**
     * 整批提交（多册同递的重载）：行上各自戴着册号，按册号归堆后一册一册过堂。
     * 各册互不相干且各自幂等，无论先后分开办还是一起办，办完账上的模样一致。
     *
     * @param rows 若干册混装的各行，每行必须戴着自己的册号
     * @return 各册收下的原表行数合计（被挡下的册计 0）
     */
    int submitBatch(List<TMetpSubmitRow> rows);

    /**
     * 整批提交（要回执的重载）：门脸专用。回话里逐行写明收下、并入还是退回，
     * 退回的指回原表第几行、因为什么退；合计与账上同源于一次推演。
     *
     * @param bill 整批提交单（册号+各行）
     * @return 核收回执，成不成见回执结论
     */
    MetpSubmitReceipt submitBatch(MetpSubmitBatch bill);

    /** 该批次的失败行明细（按行号升序） */
    List<TMetpSubmitRow> listErrors(String batchNo);

    /**
     * 翻账：册号、时次两条路都能查；退回的、收下的、进史的各码各堆，互不串门。
     *
     * @param batchNo  册号，可空
     * @param itemCode 时次要素代号，可空
     * @param status   哪一堆：0待销（现行页面只看这一堆，挪走的不露脸）、1已转存、2挂驳回；可空则不限
     * @return 在账行，按册号、行次升序
     */
    List<TMetpSubmitRow> selectTMetpSubmitRowList(String batchNo, String itemCode, Integer status);

    /**
     * 月结：收下的行满九十天挪进历史库（0待销→1已转存），退回行不动。
     *
     * @return 本次挪走的行数
     */
    int archiveDueRows();

    /**
     * 月结（指定结账时点的重载）：以 asOf 为准算满九十天，便于月结补账与核账。
     *
     * @param asOf 结账时点，为空取当前时刻
     * @return 本次挪走的行数
     */
    int archiveDueRows(Date asOf);

    /**
     * 对账：一趟 GROUP BY 同源算出各堆——挂着的（待销+挂驳回）加上转存的等于在账行数，
     * 凑不齐即是有行串了门（状态越出 0/1/2）。
     *
     * @param batchNo 册号，为空则核全账
     * @return 对账单
     */
    MetpSubmitReconcile reconcile(String batchNo);
}
