package io.rdfforge.triplestore.repository;

import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriplestoreConnectionRepository extends JpaRepository<TriplestoreConnectionEntity, UUID> {
    
    List<TriplestoreConnectionEntity> findByProjectIdOrderByNameAsc(UUID projectId);
    
    Optional<TriplestoreConnectionEntity> findByProjectIdAndIsDefaultTrue(UUID projectId);
    
    List<TriplestoreConnectionEntity> findByType(TriplestoreConnectionEntity.TriplestoreType type);
}
