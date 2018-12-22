package ca.dait.opengolf.services;

import ca.dait.opengolf.awslabs.AWSRequestSigningApacheInterceptor;
import ca.dait.opengolf.entities.course.Course;
import ca.dait.opengolf.entities.course.CourseDetails;
import ca.dait.opengolf.entities.course.CourseSearchResult;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.elasticsearch.index.query.functionscore.ExponentialDecayFunctionBuilder;
import org.elasticsearch.index.search.MatchQuery;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;

@Service
public class CourseService {

    private static final String SIGNER_SERVICE_NAME = "es";

    private static final String SEARCH_INDEX_NAME = "opengolf";
    private static final String SEARCH_TYPE_NAME = "course";
    private static final String SEARCH_FIELD_NAME = "name";
    private static final String SEARCH_FIELD_COUNTRY = "country";
    private static final String SEARCH_FIELD_HOLES = "holes";

    private static final String SEARCH_SCRIPT_FIELD_DISTANCE = "distance";
    private static final String SEARCH_SCRIPT_DISTANCE = "doc['%s'].arcDistance(%f, %f)";

    private static final String SEARCH_DISTANCE_SCALE = "2km";
    private static final String SEARCH_LAT = "lat";
    private static final String SEARCH_LON = "lon";

    private static final int SEARCH_START = 0;
    private static final int SEARCH_MAX_ROWS = 50;

    private static final String[] SEARCH_RESULT_INCLUDE_FIELDS = new String[]{SEARCH_FIELD_NAME, SEARCH_FIELD_COUNTRY};
    private static final String[] SEARCH_RESULT_EXCLUDE_FIELDS = new String[]{};

    private RestHighLevelClient searchClient;

    @Autowired
    private Gson gson;

    @Autowired
    public CourseService(@Value("${ENV_SEARCH_HOST}") String host,
                         @Value("${ENV_SEARCH_PORT}") Integer port,
                         @Value("${ENV_SEARCH_SCHEME}") String scheme,
                         @Value("${AWS_REGION}") String region){

        AWS4Signer signer = new AWS4Signer();
        signer.setServiceName(SIGNER_SERVICE_NAME);
        signer.setRegionName(region);

        HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(SIGNER_SERVICE_NAME, signer, new DefaultAWSCredentialsProviderChain());

        this.searchClient = new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, scheme))
                .setHttpClientConfigCallback(callback -> callback.addInterceptorLast(interceptor))
        );
    }

    public CourseDetails get(String id) throws IOException{
        GetRequest getRequest = new GetRequest(SEARCH_INDEX_NAME, SEARCH_TYPE_NAME, id);
        GetResponse response = this.searchClient.get(getRequest, RequestOptions.DEFAULT);
        return (response.isExists()) ? this.gson.fromJson(response.getSourceAsString(), CourseDetails.class) : null;
    }

    public CourseSearchResult search(String searchTerm, Double lat, Double lon) throws IOException{

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.from(SEARCH_START);
        searchSourceBuilder.size(SEARCH_MAX_ROWS);
        searchSourceBuilder.fetchSource(SEARCH_RESULT_INCLUDE_FIELDS, SEARCH_RESULT_EXCLUDE_FIELDS);

        QueryBuilder query = (searchTerm == null) ? QueryBuilders.matchAllQuery() :
                                    QueryBuilders.matchQuery(SEARCH_FIELD_NAME, searchTerm)
                                                .fuzziness(FuzzyQueryBuilder.DEFAULT_FUZZINESS)
                                                .zeroTermsQuery(MatchQuery.ZeroTermsQuery.ALL);

        //If co-ordinates are included, wrap the search in a distance scorer
        if(lat != null && lon != null){
            query = QueryBuilders.functionScoreQuery(query,
                    new ExponentialDecayFunctionBuilder(SEARCH_FIELD_HOLES,
                            ImmutableMap.of(SEARCH_LAT, lat, SEARCH_LON, lon), SEARCH_DISTANCE_SCALE, null)
            );

            searchSourceBuilder.scriptField(SEARCH_SCRIPT_FIELD_DISTANCE,
                    new Script(String.format(SEARCH_SCRIPT_DISTANCE, SEARCH_FIELD_HOLES, lat, lon)));
        }

        searchSourceBuilder.query(query);

        SearchResponse response = this.searchClient.search(new SearchRequest().source(searchSourceBuilder),
                                                           RequestOptions.DEFAULT);

        return new CourseSearchResult(Arrays.stream(response.getHits().getHits())
                                            .map((hit) -> {
                                                DocumentField field = hit.getFields().get(SEARCH_SCRIPT_FIELD_DISTANCE);
                                                if(field != null){
                                                    Double distance = field.getValue();
                                                    return new Course(hit.getId(), distance,
                                                            this.gson.fromJson(hit.getSourceAsString(), CourseDetails.class));
                                                }
                                                else{
                                                    return new Course(hit.getId(),
                                                            this.gson.fromJson(hit.getSourceAsString(), CourseDetails.class));
                                                }
                                            })
                                            .toArray(Course[]::new));
    }

    public Course add(CourseDetails courseDetails) throws IOException{
        IndexRequest indexRequest = new IndexRequest();
        indexRequest.index(SEARCH_INDEX_NAME);
        indexRequest.type(SEARCH_TYPE_NAME);
        indexRequest.source(this.gson.toJson(courseDetails), XContentType.JSON);
        IndexResponse indexResponse = this.searchClient.index(indexRequest, RequestOptions.DEFAULT);
        return new Course(indexResponse.getId());
    }

    public void update(String id, CourseDetails courseDetails) throws IOException{
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index(SEARCH_INDEX_NAME);
        updateRequest.type(SEARCH_TYPE_NAME);
        updateRequest.id(id);
        updateRequest.doc(this.gson.toJson(courseDetails), XContentType.JSON);
        this.searchClient.update(updateRequest, RequestOptions.DEFAULT);
    }

    public void delete(String id) throws IOException{
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(SEARCH_INDEX_NAME);
        deleteRequest.type(SEARCH_TYPE_NAME);
        deleteRequest.id(id);
        this.searchClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

}
