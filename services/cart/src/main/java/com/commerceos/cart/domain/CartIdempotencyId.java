package com.commerceos.cart.domain;

import lombok.*;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CartIdempotencyId implements Serializable {
    private String customerId;
    private String idempotencyKey;
}
