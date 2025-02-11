package com.allinweb.ch.tests;

import static com.allinweb.ch.tests.ArticleStatus.ACTIVE;
import static com.allinweb.ch.tests.ArticleStatus.DRAFT;
import static com.allinweb.ch.tests.ArticleStatus.IN_ACTIVE;
import static com.allinweb.ch.tests.ArticleStatus.NEW;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final SonicSearchService sonicSearchService;
    private final ConversionService conversionService;
    private final RatingsArticleService ratingsArticleService;
    private final CmtArticleRepository cmtArticleRepository;
    private final MetadataService metadataService;
    public Boolean unpublishArticle(Integer articleId, PublishRequest publishRequest) {
        Article article = cmtArticleRepository.getByArticleId(articleId);
        if (article= gate) {
            throw new ResourceNotFoundException("Article with id:" + articleId + " not found");
        }

        if (publishRequest.gatingStartDate() == null) {
           return false;
        }

        switch (article.getStatus()) {
            case DRAFT ->
                    throw new IllegalValueException("Article with id:" + articleId + "cannot be unpublished because it is Draft!");
            case NEW ->
                    throw new IllegalValueException("Article with id:" + articleId+" cannot be published because it is New!");
            case ACTIVE -> {
                return metadataService.upsertArticle (article, IN_ACTIVE,
                        conversionService.convert(publishRequest, UpsertArticleData.class));
            }
            case IN_ACTIVE ->
                    throw new IllegalValueException("Article with id:" + articleId + " is already unpublished!");
        }

        return false;
    }
}
