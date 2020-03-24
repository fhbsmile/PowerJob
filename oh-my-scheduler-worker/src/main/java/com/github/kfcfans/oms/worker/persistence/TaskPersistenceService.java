package com.github.kfcfans.oms.worker.persistence;


import com.github.kfcfans.oms.worker.common.constants.TaskStatus;
import com.google.common.collect.Maps;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * 任务持久化服务
 *
 * @author tjq
 * @since 2020/3/17
 */
public class TaskPersistenceService {

    private static volatile boolean initialized = false;
    public static TaskPersistenceService INSTANCE = new TaskPersistenceService();

    private TaskPersistenceService() {
    }

    private TaskDAO taskDAO = new TaskDAOImpl();
    private static final int MAX_BATCH_SIZE = 50;

    public void init() throws Exception {
        if (initialized) {
            return;
        }
        taskDAO.initTable();
    }

    public boolean save(TaskDO task) {
        boolean success = taskDAO.save(task);
        if (!success) {
            try {
                Thread.sleep(100);
                success = taskDAO.save(task);
            }catch (Exception ignore) {
            }
        }
        return success;
    }

    public boolean batchSave(List<TaskDO> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return true;
        }
        return taskDAO.batchSave(tasks);
    }


    /**
     * 获取 TaskTracker 准备派发给 Worker 执行的 task
     */
    public List<TaskDO> getNeedDispatchTask(String instanceId) {
        SimpleTaskQuery query = new SimpleTaskQuery();
        query.setInstanceId(instanceId);
        query.setStatus(TaskStatus.WAITING_DISPATCH.getValue());
        query.setLimit(100);
        return taskDAO.simpleQuery(query);
    }

    /**
     * 更新 Task 的状态
     */
    public boolean updateTaskStatus(String instanceId, String taskId, TaskStatus status, String result) {
        SimpleTaskQuery condition = new SimpleTaskQuery();
        condition.setInstanceId(instanceId);
        condition.setTaskId(taskId);
        TaskDO updateEntity = new TaskDO();
        updateEntity.setStatus(status.getValue());
        updateEntity.setResult(result);
        return taskDAO.simpleUpdate(condition, updateEntity);
    }

    /**
     * 获取 TaskTracker 管理的子 task 状态统计信息
     * TaskStatus -> num
     */
    public Map<TaskStatus, Long> getTaskStatusStatistics(String instanceId) {
        SimpleTaskQuery query = new SimpleTaskQuery();
        query.setInstanceId(instanceId);
        query.setQueryContent("status, count(*) as num");
        query.setOtherCondition("GROUP BY status");
        List<Map<String, Object>> dbRES = taskDAO.simpleQueryPlus(query);

        Map<TaskStatus, Long> result = Maps.newHashMap();
        dbRES.forEach(row -> {
            int status = Integer.parseInt(String.valueOf(row.get("status")));
            long num = Long.parseLong(String.valueOf(row.get("num")));
            result.put(TaskStatus.of(status), num);
        });
        return result;
    }

    /**
     * 获取需要被执行的任务
     */
    public List<TaskDO> getNeedRunTask(String instanceId, int limit) {

        SimpleTaskQuery query = new SimpleTaskQuery();
        query.setInstanceId(instanceId);
        query.setStatus(TaskStatus.RECEIVE_SUCCESS.getValue());
        query.setLimit(limit);

        return taskDAO.simpleQuery(query);
    }

    public int batchDelete(String instanceId, List<String> taskIds) {
        return taskDAO.batchDelete(instanceId, taskIds);
    }

    public List<TaskDO> listAll() {
        SimpleTaskQuery query = new SimpleTaskQuery();
        query.setQueryCondition("1 = 1");
        return taskDAO.simpleQuery(query);
    }
}
