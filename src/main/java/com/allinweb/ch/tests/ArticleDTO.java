package com.allinweb.ch.tests;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArticleDTO {

    private String id;

    private String title;

    private String primarySector;

    private String primarySectorCode;

    private List<String> sectors;

    private List<String> subSectors;

    private List<String> tags;

    private List<String> locations;

    private String customLocation;

    private String type;

    private String status;

    private List<String> contentClobs;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Utils.DATE_TIME_PATTERN)

    private ZonedDateTime createdDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = Utils.DATE_TIME_PATTERN)

    private ZonedDateTime publishedDate;

    private String primaryAuthor;

    private String ratingsSourceObjectId;

    private String contentCustomUrl;

    private String contentLanguage;

}
