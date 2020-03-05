package org.analyticalserver.ohlc.worker2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
@Builder
public class BarChartData {
    private final String event = "ohlc_notify";
    private String symbol;
    private Long barNumber;
    private Double o;
    private Double h;
    private Double l;
    private Double c;
    private Double volume;
    @ToString.Exclude
    private Double currentValue;
    @ToString.Exclude
    private Double currentQuantity;
}
