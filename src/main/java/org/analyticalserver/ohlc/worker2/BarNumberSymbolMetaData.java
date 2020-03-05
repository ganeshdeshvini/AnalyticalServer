package org.analyticalserver.ohlc.worker2;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class BarNumberSymbolMetaData {
    private Double high;
    private Double low;
    private Double open;
}
