package ca.dait.opengolf.services;

import ca.dait.opengolf.awslabs.AWSRequestSigningApacheInterceptor;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.search.MatchQuery;
import org.elasticsearch.search.SearchHit;
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
    private static final int SEARCH_START = 0;
    private static final int SEARCH_MAX_ROWS = 50;

    private static final String[] SEARCH_RESULT_INCLUDE_FIELDS = new String[]{SEARCH_FIELD_NAME, SEARCH_FIELD_COUNTRY};
    private static final String[] SEARCH_RESULT_EXCLUDE_FIELDS = new String[]{};

    private RestHighLevelClient searchClient;

    @Autowired
    private ObjectMapper mapper;

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
        return (response.isExists()) ? this.mapper.readValue(response.getSourceAsBytes(), CourseDetails.class) : null;
    }

    public CourseSearchResult search(String searchTerm) throws IOException{

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.from(SEARCH_START);
        searchSourceBuilder.size(SEARCH_MAX_ROWS);
        searchSourceBuilder.fetchSource(SEARCH_RESULT_INCLUDE_FIELDS, SEARCH_RESULT_EXCLUDE_FIELDS);

        searchSourceBuilder.query(QueryBuilders.matchQuery(SEARCH_FIELD_NAME, searchTerm)
                                               .fuzziness(FuzzyQueryBuilder.DEFAULT_FUZZINESS)
                                               .zeroTermsQuery(MatchQuery.ZeroTermsQuery.ALL));

        SearchResponse response = this.searchClient.search(new SearchRequest().source(searchSourceBuilder),
                                                           RequestOptions.DEFAULT);

        return new CourseSearchResult(Arrays.stream(response.getHits().getHits())
                                            .map(this::searchHitToCourse)
                                            .toArray(Course[]::new));
    }

    public Course add(CourseDetails courseDetails) throws IOException{
        IndexRequest indexRequest = new IndexRequest();
        indexRequest.index(SEARCH_INDEX_NAME);
        indexRequest.type(SEARCH_TYPE_NAME);
        indexRequest.source(this.mapper.writeValueAsBytes(courseDetails), XContentType.JSON);
        IndexResponse indexResponse = this.searchClient.index(indexRequest, RequestOptions.DEFAULT);
        return new Course(indexResponse.getId());
    }

    public void update(String id, CourseDetails courseDetails) throws IOException{
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index(SEARCH_INDEX_NAME);
        updateRequest.type(SEARCH_TYPE_NAME);
        updateRequest.id(id);
        updateRequest.doc(this.mapper.writeValueAsBytes(courseDetails), XContentType.JSON);
        this.searchClient.update(updateRequest, RequestOptions.DEFAULT);
    }

    public void delete(String id) throws IOException{
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index(SEARCH_INDEX_NAME);
        deleteRequest.type(SEARCH_TYPE_NAME);
        deleteRequest.id(id);
        this.searchClient.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    private Course searchHitToCourse(SearchHit hit){
        try {
            return new Course(hit.getId(),
                    this.mapper.readValue(hit.getSourceAsString(), CourseDetails.class));
        }
        catch(IOException e){
            throw new RuntimeException("Failed to marshal SearchHit result into " + CourseDetails.class.getName(), e);
        }
    }

    /*
        Java POJO's for the course service.
    */
    public static class CourseSearchResult {
        private Course results[];

        public CourseSearchResult(Course results[]){
            this.results = results;
        }
    }

    public static class CourseDetails {
        private String name;
        private String country;
        private CourseDetails.Hole holes[];

        @JsonCreator
        public CourseDetails(@JsonProperty("name") String name,
                             @JsonProperty("country") String country,
                             @JsonProperty("holes") CourseDetails.Hole holes[]){
            this.name = name;
            this.country = country;
            this.holes = holes;
        }

        public static class Hole{
            private double lat;
            private double lon;

            @JsonCreator
            public Hole(@JsonProperty("lat") double lat,
                        @JsonProperty("lon") double lon){
                this.lat = lat;
                this.lon = lon;
            }
        }
    }

    public static class Course {
        private String id;
        private CourseDetails details;

        public Course(String id){
            this.id = id;
        }

        public Course(String id, CourseDetails details){
            this.id = id;
            this.details = details;
        }
    }

}
