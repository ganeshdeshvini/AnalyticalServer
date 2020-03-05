package org.analyticalserver.ohlc.worker2;

import java.util.Objects;

public class BarNumberSymbol {
    private Long barNumber;
    private String symbol;

    public BarNumberSymbol(Long barNumber, String symbol) {
        this.barNumber = barNumber;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BarNumberSymbol that = (BarNumberSymbol) o;
        return barNumber.equals(that.barNumber) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(barNumber, symbol);
    }
}
