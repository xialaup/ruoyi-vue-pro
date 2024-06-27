package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.service.task.BpmActivityService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableActivityCancelledEvent;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 监听 {@link Task} 的开始与完成
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmTaskEventListener extends AbstractFlowableEngineEventListener {

    @Resource
    @Lazy // 解决循环依赖
    private BpmTaskService taskService;
    @Resource
    @Lazy // 解决循环依赖
    private BpmActivityService activityService;

    public static final Set<FlowableEngineEventType> TASK_EVENTS = ImmutableSet.<FlowableEngineEventType>builder()
            .add(FlowableEngineEventType.TASK_CREATED)
            .add(FlowableEngineEventType.TASK_ASSIGNED)
//            .add(FlowableEngineEventType.TASK_COMPLETED) // 由于审批通过时，已经记录了 task 的 status 为通过，所以不需要监听了。
            .add(FlowableEngineEventType.ACTIVITY_CANCELLED)
            .build();

    public BpmTaskEventListener() {
        super(TASK_EVENTS);
    }

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        taskService.updateTaskStatusWhenCreated((Task) event.getEntity());
    }

    @Override
    protected void taskAssigned(FlowableEngineEntityEvent event) {
        taskService.updateTaskExtAssign((Task) event.getEntity());
    }

    @Override
    protected void activityCancelled(FlowableActivityCancelledEvent event) {
        // TODO @jason：如果用户主动取消，可能需要考虑这个
        // TODO @芋艿 如果在 rejectTask 处理了。 这里又要查询一次。感觉有点多余。 主动取消是不是也要处理一下。要不然有加签的任务也会报错
        // @芋艿。 这里是不是就可以不要了， 取消的任务状态，在rejectTask 里面做了， 如果在 updateTaskStatusWhenCanceled 里面修改会报错。
        List<HistoricActivityInstance> activityList = activityService.getHistoricActivityListByExecutionId(event.getExecutionId());
        if (CollUtil.isEmpty(activityList)) {
            log.error("[activityCancelled][使用 executionId({}) 查找不到对应的活动实例]", event.getExecutionId());
            return;
        }
        // 遍历处理
        activityList.forEach(activity -> {
            if (StrUtil.isEmpty(activity.getTaskId())) {
                return;
            }
            taskService.updateTaskStatusWhenCanceled(activity.getTaskId());
        });
    }

}
