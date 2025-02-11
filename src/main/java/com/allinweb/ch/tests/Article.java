package com.allinweb.ch.tests;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import java.time.LocalDateTime;
import java.util.HashSet;

@EqualsAndHashCode(callsuper = true)
@Data
@Entity
@Table(name = "cnt articles")
public class Article extends Auditable {
    @Id
    @GeneratedValue(strategy GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "article_id")
    private Integer articleId;

    @Column(name = "kou_id")
    private Integer kould;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ArticleStatus status;

    @Column(name = "content_custom_url")
    private String contentCustomUrl;

    @Column(name = "gating_enabled")
    private Boolean gatingEnabled;

    @Column(name = "gating_start_date")
    private LocalDateTime gatingStartDate;

    @Column(name = "gating_end_date")
    private LocalDateTime gatingEndDate;


    @ManyToMany
    @JoinTable(name = "cmt_article_tags", 
            joinColumns = @JoinColumn(name = "article_id"), 
            inverseJoinColumns = @JoinColumn(name = "tag_id")

    )

    private Set<Tag> additionalTags = new HashSet<>();

    @ManyToMany
    JoinTable(name ="cmt_article_sectors", 
              joinColumns = @JoinColumn(name="article_id"),inverseJoinColumns =
    @JoinColumn(name = "sector_id")

)

    private Set<Sector> additionalSectors = new HashSet<>();

    @Many
    ToMany

    @JoinTable(name = "cmt_article_locations",

            joinColumns = @JoinColumn(name = "article_id"), inverseJoinColumns = @JoinColumn(name = "location_id")

    )

    private Set<Location> additionallocations = new HashSet<>();

    @Many
    ToMany

    @JoinTable(name = "cat_article_topics",

            joinColumns = @JoinColumn(name = "article_id"), inverseJoinColumns@JoinColumn(name = "topic_id")

    )

    private Set<Topic> additionalTopics new HashSet<>();
}