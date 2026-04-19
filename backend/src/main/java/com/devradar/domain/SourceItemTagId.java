package com.devradar.domain;

import java.io.Serializable;
import java.util.Objects;

public class SourceItemTagId implements Serializable {
    private Long sourceItemId;
    private Long interestTagId;

    public SourceItemTagId() {}
    public SourceItemTagId(Long sourceItemId, Long interestTagId) {
        this.sourceItemId = sourceItemId; this.interestTagId = interestTagId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceItemTagId other)) return false;
        return Objects.equals(sourceItemId, other.sourceItemId) && Objects.equals(interestTagId, other.interestTagId);
    }
    @Override public int hashCode() { return Objects.hash(sourceItemId, interestTagId); }
}
