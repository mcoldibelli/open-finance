package br.com.codaline.reconciliation.batch;

import java.math.BigDecimal;

public record CipTransaction(String endToEndId,
                             String debtorIspb,
                             String creditorIspb,
                             BigDecimal amount) {

}
