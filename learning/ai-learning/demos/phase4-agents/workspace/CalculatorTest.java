public class CalculatorTest {
    public static void main(String[] args) {
        int r1 = Calculator.add(2, 3);
        if (r1 != 5) throw new AssertionError("add(2,3) verwachtte 5, kreeg " + r1);

        int r2 = Calculator.subtract(5, 3);
        if (r2 != 2) throw new AssertionError("subtract(5,3) verwachtte 2, kreeg " + r2);

        System.out.println("ALLE TESTS GESLAAGD");
    }
}
