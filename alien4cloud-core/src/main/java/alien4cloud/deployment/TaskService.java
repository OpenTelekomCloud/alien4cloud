package alien4cloud.deployment;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.model.runtime.Execution;
import alien4cloud.model.runtime.Task;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Manage {@link Task} operations.
 */
@Service
@Slf4j
public class TaskService {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDao;

    /**
     * Search for {@link Task}s. See below to known with filters are supported.
     *
     * @param query Query text.
     * @param executionId Id of the execution for which to get tasks.
     * @param from Query from the given index.
     * @param size Maximum number of results to retrieve.
     * @return the tasks with pagination
     */
    public FacetedSearchResult<Task> searchTasks(String query, String executionId, int from, int size) {
        QueryBuilder filterBuilder = buildFilters(executionId);
        return alienDao.facetedSearch(Task.class, query, null, filterBuilder, null, from, size, "scheduleDate", "date", true);
    }

    private QueryBuilder buildFilters(String executionId) {
        QueryBuilder filterBuilder = null;
        if (executionId != null) {
            QueryBuilder filter = QueryBuilders.termQuery("executionId", executionId);
            //filterBuilder = filterBuilder == null ? filter : QueryBuilders.andQuery(filter, filterBuilder);
            filterBuilder = QueryBuilders.boolQuery().must(filter);
        }
        return filterBuilder;
    }

}
