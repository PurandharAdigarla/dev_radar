package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "feed_subscriptions",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_feed_subscriptions_tag_url",
           columnNames = {"tag_slug", "feed_url"}))
public class FeedSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_slug", nullable = false, length = 100)
    private String tagSlug;

    @Column(name = "feed_url", nullable = false, length = 512)
    private String feedUrl;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getTagSlug() { return tagSlug; }
    public String getFeedUrl() { return feedUrl; }
    public String getTitle() { return title; }
    public boolean isActive() { return active; }
}
