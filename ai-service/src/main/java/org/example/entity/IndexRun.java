package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One row per catalog-embedding indexer run (table {@code index_state}) — powers the admin status endpoint. */
@Entity
@Getter
@Setter
@Table(name = "index_state")
public class IndexRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_run_at", nullable = false)
    private Instant lastRunAt;

    @Column(name = "last_status", nullable = false)
    private String lastStatus;

    @Column(name = "products_indexed", nullable = false)
    private Integer productsIndexed = 0;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
