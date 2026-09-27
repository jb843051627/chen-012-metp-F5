package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.model.custom.MetpSubmitBatch;
import com.fc.v2.model.custom.MetpSubmitReceipt;
import com.fc.v2.model.custom.MetpSubmitReconcile;
import com.fc.v2.service.ITMetpSubmitRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 正点观测数据核收转存行 Controller（batch-process 形状：门脸只认整批提交，
 * 一行行拆与账都是服务层的差事；单添一行单改一行的门不辟）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "正点观测数据核收转存行")
@Controller
@RequestMapping("/metpSubmitRow")
public class MetpSubmitRowController extends BaseController {

    @Autowired
    private ITMetpSubmitRowService metpSubmitRowService;

    @Log(title = "正点观测数据整批提交", action = "submitBatch")
    @ApiOperation(value = "整批提交", notes = "门脸唯一认的一号：整批提交。这一回批成什么账由系统一行一行推演落定，成不成回话见分晓")
    @PostMapping("/submitBatch")
    @RequiresPermissions("metpSubmitRow:submit")
    @ResponseBody
    public AjaxResult submitBatch(@RequestBody MetpSubmitBatch bill) {
        MetpSubmitReceipt receipt = metpSubmitRowService.submitBatch(bill);
        return success(200, receipt.getResult(), receipt);
    }

    @Log(title = "正点观测数据翻账", action = "list")
    @ApiOperation(value = "翻账", notes = "册号、时次两条路都能查；status 选定哪一堆：0待销（现行页面）、1已转存、2挂驳回，各码各堆互不串门")
    @GetMapping("/list")
    @RequiresPermissions("metpSubmitRow:list")
    @ResponseBody
    public ResultTable list(String batchNo, String itemCode, Integer status) {
        startPage();
        com.github.pagehelper.PageInfo<TMetpSubmitRow> page =
                new com.github.pagehelper.PageInfo<TMetpSubmitRow>(
                        metpSubmitRowService.selectTMetpSubmitRowList(batchNo, itemCode, status));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "正点观测数据退回明细", action = "errors")
    @ApiOperation(value = "退回明细", notes = "该册退回去的几行，指回原表第几行、因为什么退，按行号升序")
    @GetMapping("/errors")
    @RequiresPermissions("metpSubmitRow:list")
    @ResponseBody
    public ResultTable errors(String batchNo) {
        return dataTable(metpSubmitRowService.listErrors(batchNo));
    }

    @Log(title = "正点观测数据对账", action = "reconcile")
    @ApiOperation(value = "对账", notes = "挂着的加上转存的等于在账行数；册号为空则核全账")
    @GetMapping("/reconcile")
    @RequiresPermissions("metpSubmitRow:list")
    @ResponseBody
    public AjaxResult reconcile(String batchNo) {
        MetpSubmitReconcile reconcile = metpSubmitRowService.reconcile(batchNo);
        return retobject(200, reconcile);
    }
}
