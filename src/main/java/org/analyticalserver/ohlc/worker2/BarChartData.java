package org.analyticalserver.ohlc.worker2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@Builder
public class BarChartData {
    private final String event = "ohlc_notify";
    private String symbol;
    private Long barNumber;
    private BigDecimal o;
    private BigDecimal h;
    private BigDecimal l;
    private BigDecimal c;
    private BigDecimal volume;
    @JsonIgnore
    private BigDecimal currentValue;
    @JsonIgnore
    private BigDecimal currentQuantity;
}
