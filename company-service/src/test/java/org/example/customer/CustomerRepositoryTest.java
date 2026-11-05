package org.example.customer;

import jakarta.persistence.EntityManager;
import org.example.dto.customer.CustomerSummary;
import org.example.entity.Company;
import org.example.entity.Customer;
import org.example.enums.CompanyType;
import org.example.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
@ContextConfiguration(classes = CustomerRepositoryTest.Config.class)
class CustomerRepositoryTest {
    @Configuration
    @EntityScan(basePackageClasses = Customer.class)
    @EnableJpaRepositories(basePackageClasses = CustomerRepository.class)
    static class Config {
    }

    @Autowired EntityManager em;
    @Autowired CustomerRepository customers;

    @Test
    void scopesSearchAndSummaryToCompanyActiveRecordsAndWholeFilteredResult() {
        Company first = company("First", 10L);
        Company second = company("Second", 20L);
        customer(first, "Baraka", "SH-1", 10, "file.pdf", false);
        customer(first, "Another", "SH-2", 20, null, false);
        customer(first, "Archived", "SH-3", 99, "old.pdf", true);
        customer(second, "Other company", "SH-4", 100, "other.pdf", false);
        em.flush();
        em.clear();

        var page = customers.search(first.getId(), "", null, PageRequest.of(0, 1));
        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        CustomerSummary summary = customers.summarize(first.getId(), "", null);
        assertEquals(2, summary.customersCount());
        assertEquals(1, summary.withContractFileCount());
        assertEquals(15.0, summary.averagePaymentTermDays());
        assertEquals(1, customers.search(first.getId(), "baraka", true, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, customers.search(first.getId(), "sh-2", false, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(0, customers.search(first.getId(), "%", null, PageRequest.of(0, 20)).getTotalElements());
    }

    private Company company(String name, Long owner) {
        Company company = new Company();
        company.setName(name);
        company.setOwnerUserId(owner);
        company.setType(CompanyType.values()[0]);
        em.persist(company);
        return company;
    }

    private void customer(Company company, String name, String contract, int days, String file, boolean archived) {
        Customer customer = new Customer();
        customer.setCompany(company);
        customer.setName(name);
        customer.setContractNumber(contract);
        customer.setPaymentTermDays(days);
        customer.setContractAttachId(file);
        customer.setDeleted(archived);
        em.persist(customer);
    }
}
