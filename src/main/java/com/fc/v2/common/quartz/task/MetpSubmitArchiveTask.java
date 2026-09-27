package com.fc.v2.common.quartz.task;

import com.fc.v2.service.ITMetpSubmitRowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 正点观测数据月结任务：收下的行满九十天挪进历史库。
 * 在 quartz 作业表登记 metpSubmitArchiveTask.moveMonthlyToHistory，cron 按月（出账按月走）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Component("metpSubmitArchiveTask")
public class MetpSubmitArchiveTask {

    @Autowired
    private ITMetpSubmitRowService metpSubmitRowService;

    /**
     * 月结挪史：0待销 → 1已转存，退回行不动；挪走的行现行页面不再露脸，只留册号、时次两条路可查
     */
    public void moveMonthlyToHistory() {
        int moved = metpSubmitRowService.archiveDueRows();
        System.out.println("正点观测数据月结：挪进历史库 " + moved + " 行");
    }
}
