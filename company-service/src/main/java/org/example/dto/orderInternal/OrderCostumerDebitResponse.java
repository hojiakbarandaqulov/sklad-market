package org.example.dto.orderInternal;


import java.util.List;

public record OrderCostumerDebitResponse (
        Long companyId,
        List<Costumers> costumers
){

}
