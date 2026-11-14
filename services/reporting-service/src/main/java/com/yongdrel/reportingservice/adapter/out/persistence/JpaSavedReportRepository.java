package com.yongdrel.reportingservice.adapter.out.persistence;

import com.yongdrel.reportingservice.application.port.out.SavedReportRepositoryPort;
import com.yongdrel.reportingservice.domain.model.SavedReport;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaSavedReportRepository implements SavedReportRepositoryPort {
    private final EntityManager em;
    public JpaSavedReportRepository(EntityManager em){this.em=em;}
    public SavedReport save(SavedReport v){return em.merge(SavedReportEntity.from(v)).domain();}
    public List<SavedReport> findByTenant(Long tenantId){return em.createQuery("select r from SavedReportEntity r where r.tenantId=:tenantId order by r.updatedAt desc",SavedReportEntity.class).setParameter("tenantId",tenantId).getResultStream().map(SavedReportEntity::domain).toList();}
    public Optional<SavedReport> findById(Long id){return Optional.ofNullable(em.find(SavedReportEntity.class,id)).map(SavedReportEntity::domain);}
    public void deleteById(Long id){var r=em.find(SavedReportEntity.class,id);if(r!=null)em.remove(r);}
    public boolean nameTaken(Long tenantId,String name,Long excludeId){return em.createQuery("select count(r) from SavedReportEntity r where r.tenantId=:tenantId and r.name=:name and (:excludeId is null or r.id<>:excludeId)",Long.class).setParameter("tenantId",tenantId).setParameter("name",name).setParameter("excludeId",excludeId).getSingleResult()>0;}
}
