package org.example.dto.order;

import lombok.Getter;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;

public record OrderCostumerDebitResponse (
        Long companyId,
        List<Costumers> costumers
){

}
