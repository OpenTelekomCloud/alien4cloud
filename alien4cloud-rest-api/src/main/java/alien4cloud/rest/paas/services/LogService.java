package alien4cloud.rest.paas.services;

import alien4cloud.dao.IESSearchQueryBuilderHelper;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.dao.QueryUtils;
import alien4cloud.dao.model.FacetedSearchResult;
import alien4cloud.dao.model.GetMultipleDataResult;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.model.PaaSDeploymentLog;
import alien4cloud.rest.application.model.SearchLogRequest;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.rest.model.RestResponseBuilder;
import alien4cloud.rest.model.SortConfiguration;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;

@Service
public class LogService {

    @Resource(name = "alien-monitor-es-dao")
    private IGenericSearchDAO alienMonitorDao;
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    /**
     * Factorise call to search service
     *
     * @param searchRequest
     * @return
     */
    public RestResponse<FacetedSearchResult<PaaSDeploymentLog>> doSearch(SearchLogRequest searchRequest) {
        IESSearchQueryBuilderHelper<PaaSDeploymentLog> query = buildQuery(searchRequest);
        return RestResponseBuilder.<FacetedSearchResult<PaaSDeploymentLog>> builder()
                .data(query.facetedSearch(searchRequest.getFrom(), searchRequest.getSize())).build();
    }

    private IESSearchQueryBuilderHelper<PaaSDeploymentLog> buildQuery(SearchLogRequest searchRequest) {
        RangeQueryBuilder dateRangeBuilder = QueryUtils.buildTimestampRangeQuery(searchRequest.getFromDate(),searchRequest.getToDate());

        String sortBy = "timestamp";
        boolean ascending = false;
        if (searchRequest.getSortConfiguration() != null) {
            sortBy = searchRequest.getSortConfiguration().getSortBy();
            ascending = searchRequest.getSortConfiguration().isAscending();
        }

        IESSearchQueryBuilderHelper<PaaSDeploymentLog> query;
        if (dateRangeBuilder == null) {
            query = alienMonitorDao.buildSearchQuery(PaaSDeploymentLog.class, searchRequest.getQuery()).prepareSearch().setFilters(searchRequest.getFilters())
                    .setFieldSort(sortBy, "date", !ascending);

        } else {
            query = alienMonitorDao.buildSearchQuery(PaaSDeploymentLog.class, searchRequest.getQuery()).prepareSearch()
                    .setFilters(searchRequest.getFilters(), dateRangeBuilder).setFieldSort(sortBy, "date", !ascending);
        }
        return query;
    }

    public String getDownloadFileName(SearchLogRequest request) {
        String deploymentId = request.getFilters().get("deploymentId")[0];
        Deployment deployment = alienDAO.findById(Deployment.class, deploymentId);
        return deployment.getOrchestratorDeploymentId() + "-" + deployment.getVersionId() + ".log";
    }

    public void downloadLogs(SearchLogRequest request, ServletOutputStream os) {
        final int pageSize = 100;
        int from = 0;
        request.setSortConfiguration(new SortConfiguration("timestamp", true));
        final IESSearchQueryBuilderHelper<PaaSDeploymentLog> query = buildQuery(request);
        while (true) {
            GetMultipleDataResult<PaaSDeploymentLog> result = query.search(from, pageSize);
            List<PaaSDeploymentLog> logList = Arrays.asList(result.getData());
            logList.forEach(log -> {
                try {
                    os.print(log.toFormattedString());
                } catch (IOException e) {
                    throw new RuntimeException("Error when downloading logs.", e);
                }
            });
            from += pageSize;
            if (result.getTotalResults() < pageSize) {
                break;
            }
        }
    }
}
