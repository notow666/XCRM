package cn.cordys.crm.system.service;


import cn.cordys.common.schedule.ScheduleManager;

import cn.cordys.common.util.JSON;

import cn.cordys.crm.system.domain.Schedule;

import cn.cordys.crm.system.mapper.ExtScheduleMapper;

import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.BooleanUtils;

import org.quartz.Job;

import org.quartz.JobKey;

import org.quartz.TriggerKey;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service

@Transactional(rollbackFor = Exception.class)

@Slf4j

public class ExtScheduleService {


    @Resource

    private BaseMapper<Schedule> scheduleMapper;

    @Resource

    private ScheduleManager scheduleManager;

    @Resource

    private ExtScheduleMapper extScheduleMapper;


    public void startEnableSchedules() {
        syncEnabledSchedulesForCurrentTenant();
    }


    /**
     * 将当前租户库中的 schedule 同步到 master Quartz（启用则注册/更新，禁用则移除）。
     */

    public void syncEnabledSchedulesForCurrentTenant() {

        long count = scheduleMapper.countByExample(new Schedule());

        long pages = (long) Math.ceil(count / 100.0);


        for (int i = 0; i < pages; i++) {

            int start = i * 100;

            List<Schedule> schedules = extScheduleMapper.getScheduleByLimit(start, 100);

            doHandleSchedule(schedules);

        }

    }


    private void doHandleSchedule(List<Schedule> schedules) {

        schedules.forEach(schedule -> {

            try {

                if (BooleanUtils.isTrue(schedule.getEnable())) {

                    log.info("初始化任务：{}", JSON.toJSONString(schedule));

                    @SuppressWarnings("unchecked")

                    Class<? extends Job> jobClass = (Class<? extends Job>) Class.forName(schedule.getJob());

                    scheduleManager.addOrUpdateCronJob(

                            new JobKey(schedule.getKey(), schedule.getJob()),

                            new TriggerKey(schedule.getKey(), schedule.getJob()),

                            jobClass,

                            schedule.getValue(),

                            scheduleManager.getDefaultJobDataMap(schedule, schedule.getValue(), schedule.getCreateUser())

                    );

                } else {

                    removeJob(schedule);

                }

            } catch (ClassNotFoundException e) {

                log.error("任务类未找到：{}", schedule.getJob(), e);

            } catch (Exception e) {

                log.error("初始化任务失败", e);

            }

        });

    }


    private void removeJob(Schedule schedule) {

        scheduleManager.removeJob(

                new JobKey(schedule.getKey(), schedule.getJob()),

                new TriggerKey(schedule.getKey(), schedule.getJob())

        );

    }

}

