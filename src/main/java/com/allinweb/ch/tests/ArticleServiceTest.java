package com.allinweb.ch.tests;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    SonicSearchService sonicSearchService;

    @Mock
    private AppConfig appConfig;

    private ArticleService articleService;

    @Mock
    RatingsArticleService ratingsArticleService;

    @Hock
    CatArticleRepository catArticleRepository;

    @Hock
    HetadataService metadataService;

    @BeforeEach
    void setup() {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new ConvertSonicDisplayDataToArticle());
        conversionService.addConverter(new ConvertPublishRequestToUpsertArticleData());
        articleService new ArticleService(sonicSearchService,
                conversionService, ratingsArticleService,
                cmtArticleRepository, metadataService);
    }

    private SearchRequest createValidSearchRequest() {

        return new SearchRequest(
                null,
                "test",
                List.of("sector1", "sector2"),
                List.of("subSector1"),
                List.of("type1"),
                List.of("subType1"),
                List.of("status1"), List.of("location1"),
                "2024-01-18", "2025-01-18",
                0,
                10);

    }

    private SonicSearchResult getlockSonicSearchResponse() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(TestUtils.readTestDataFile("/testdata/service/1-sonic-search-result.json"),
                SonicSearchResult.class);

    }
}