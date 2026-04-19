package com.devradar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "source_item_tags")
@IdClass(SourceItemTagId.class)
public class SourceItemTag {
    @Id @Column(name = "source_item_id") private Long sourceItemId;
    @Id @Column(name = "interest_tag_id") private Long interestTagId;

    public SourceItemTag() {}
    public SourceItemTag(Long sourceItemId, Long interestTagId) {
        this.sourceItemId = sourceItemId; this.interestTagId = interestTagId;
    }
    public Long getSourceItemId() { return sourceItemId; }
    public Long getInterestTagId() { return interestTagId; }
}
