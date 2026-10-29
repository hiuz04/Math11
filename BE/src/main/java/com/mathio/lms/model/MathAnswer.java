package com.mathio.lms.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/** Exact, bounded rational polynomial algebra; unsupported mathematics stays UNKNOWN. */
public final class MathAnswer {
    public enum Comparison {
        EQUIVALENT,
        NOT_EQUIVALENT,
        UNKNOWN
    }

    public static boolean matches(String a, String b) {
        return compare(a, b) == Comparison.EQUIVALENT;
    }

    public static Comparison compare(String a, String b) {
        try {
            if (a != null && b != null && a.contains("=") && b.contains("=")) {
                Set<Character> av = new HashSet<>(), bv = new HashSet<>();
                for (char ch : a.toCharArray()) if (Character.isLetter(ch)) av.add(ch);
                for (char ch : b.toCharArray()) if (Character.isLetter(ch)) bv.add(ch);
                if (!av.equals(bv)) return Comparison.UNKNOWN;
            }
            List<Expr> left = answers(normalize(a)), right = answers(normalize(b));
            if (left.size() != right.size()) return Comparison.NOT_EQUIVALENT;
            boolean unknown = false;
            boolean[] used = new boolean[right.size()];
            for (Expr x : left) {
                int found = -1;
                boolean maybe = false;
                for (int i = 0; i < right.size(); i++)
                    if (!used[i]) {
                        Comparison c = x.compare(right.get(i));
                        if (c == Comparison.EQUIVALENT) {
                            found = i;
                            break;
                        }
                        if (c == Comparison.UNKNOWN) maybe = true;
                    }
                if (found < 0) {
                    if (!maybe) return Comparison.NOT_EQUIVALENT;
                    unknown = true;
                } else used[found] = true;
            }
            return unknown ? Comparison.UNKNOWN : Comparison.EQUIVALENT;
        } catch (IllegalArgumentException | ArithmeticException e) {
            return Comparison.UNKNOWN;
        }
    }

    public static double value(String text) {
        Expr e = parse(normalize(text));
        Q q = e.constant();
        if (q == null) throw new IllegalArgumentException();
        return q.n.doubleValue() / q.d.doubleValue();
    }

    private static String normalize(String s) {
        if (s == null || s.length() > 1000) throw new IllegalArgumentException();
        s =
                s.replace("\\left", "")
                        .replace("\\right", "")
                        .replace("\\,", "")
                        .replace("\\;", "")
                        .replace("\\cdot", "*")
                        .replace("\\times", "*")
                        .replace("\\div", "/")
                        .replace("\\dfrac", "\\frac")
                        .replace("\\tfrac", "\\frac")
                        .replace("\\{", "{")
                        .replace("\\}", "}")
                        .replace("\\in", "∈")
                        .replaceAll("\\s+", "")
                        .replace("−", "-")
                        .replace("×", "*")
                        .replace("·", "*")
                        .replace("**", "^")
                        .replace("$", "");
        // Expand nested LaTeX fractions by balanced groups, without evaluating input code.
        while (s.contains("\\frac")) {
            int i = s.lastIndexOf("\\frac"), first = i + 5;
            int end1 = groupEnd(s, first), end2 = groupEnd(s, end1 + 1);
            s =
                    s.substring(0, i)
                            + "(("
                            + s.substring(first + 1, end1)
                            + ")/("
                            + s.substring(end1 + 2, end2)
                            + "))"
                            + s.substring(end2 + 1);
        }
        if (s.matches(".*[a-zA-Z]{2,}.*")) throw new IllegalArgumentException();
        return s;
    }

    private static int groupEnd(String s, int start) {
        if (start >= s.length() || s.charAt(start) != '{') throw new IllegalArgumentException();
        return closeGroup(s, start);
    }

    private static int closeGroup(String s, int start) {
        int d = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '{') d++;
            else if (s.charAt(i) == '}' && --d == 0) return i;
        }
        throw new IllegalArgumentException();
    }

    private static List<Expr> answers(String s) {
        if (s.matches("[sS]=\\{.*")) s = s.substring(2);
        int membership = s.indexOf('∈');
        if (membership >= 0) {
            if (!s.substring(0, membership).matches("[a-zA-Z]"))
                throw new IllegalArgumentException();
            s = s.substring(membership + 1);
        }
        if (s.startsWith("{") && s.endsWith("}")) {
            String body = s.substring(1, s.length() - 1);
            if (body.isEmpty()) return List.of();
            List<Expr> out = new ArrayList<>();
            for (String item : body.split("[,;]", -1)) {
                Expr e = parse(item);
                if (!e.domains.isEmpty()) throw new IllegalArgumentException();
                if (out.stream().noneMatch(x -> x.compare(e) == Comparison.EQUIVALENT)) out.add(e);
            }
            if (out.size() > 32) throw new IllegalArgumentException();
            return out;
        }
        if (s.contains("=")) {
            String[] sides = s.split("=", -1);
            if (sides.length != 2) throw new IllegalArgumentException();
            Expr diff = parse(sides[0]).sub(parse(sides[1]));
            Set<String> vars = new HashSet<>();
            diff.n.terms.keySet().forEach(m -> vars.addAll(m.powers.keySet()));
            if (vars.size() != 1 || !diff.domains.isEmpty()) throw new IllegalArgumentException();
            String variable = vars.iterator().next();
            Q[] coefficients = {Q.ZERO, Q.ZERO, Q.ZERO};
            for (var t : diff.n.terms.entrySet()) {
                int degree = t.getKey().powers.getOrDefault(variable, 0);
                if (degree > 2) throw new IllegalArgumentException();
                coefficients[degree] = t.getValue();
            }
            Q c = coefficients[0], b = coefficients[1], a = coefficients[2];
            if (a.zero()) {
                if (b.zero()) throw new IllegalArgumentException();
                return List.of(Expr.scalar(c.neg().div(b)));
            }
            Q disc = b.mul(b).sub(new Q(4).mul(a).mul(c));
            if (disc.n.signum() < 0) return List.of();
            BigInteger rn = disc.n.sqrt(), rd = disc.d.sqrt();
            if (!rn.multiply(rn).equals(disc.n) || !rd.multiply(rd).equals(disc.d))
                throw new IllegalArgumentException();
            Q root = new Q(rn, rd), denom = new Q(2).mul(a);
            if (root.zero()) return List.of(Expr.scalar(b.neg().div(denom)));
            return List.of(
                    Expr.scalar(b.neg().add(root).div(denom)),
                    Expr.scalar(b.neg().sub(root).div(denom)));
        }
        return List.of(parse(s));
    }

    private record Q(BigInteger n, BigInteger d) {
        static final Q ZERO = new Q(0), ONE = new Q(1);

        Q(long n) {
            this(BigInteger.valueOf(n), BigInteger.ONE);
        }

        Q {
            if (d.signum() == 0) throw new ArithmeticException();
            if (d.signum() < 0) {
                n = n.negate();
                d = d.negate();
            }
            BigInteger gcd = n.gcd(d);
            n = n.divide(gcd);
            d = d.divide(gcd);
            if (n.bitLength() > 4096 || d.bitLength() > 4096) throw new IllegalArgumentException();
        }

        boolean zero() {
            return n.signum() == 0;
        }

        Q add(Q q) {
            return new Q(n.multiply(q.d).add(q.n.multiply(d)), d.multiply(q.d));
        }

        Q neg() {
            return new Q(n.negate(), d);
        }

        Q sub(Q q) {
            return add(q.neg());
        }

        Q mul(Q q) {
            return new Q(n.multiply(q.n), d.multiply(q.d));
        }

        Q div(Q q) {
            return new Q(n.multiply(q.d), d.multiply(q.n));
        }
    }

    private record Mono(SortedMap<String, Integer> powers) implements Comparable<Mono> {
        static final Mono ONE = new Mono(new TreeMap<>());

        Mono {
            powers = Collections.unmodifiableSortedMap(new TreeMap<>(powers));
        }

        Mono mul(Mono m) {
            TreeMap<String, Integer> r = new TreeMap<>(powers);
            m.powers.forEach((k, v) -> r.merge(k, v, Integer::sum));
            if (r.values().stream().mapToInt(Integer::intValue).sum() > 64)
                throw new IllegalArgumentException();
            return new Mono(r);
        }

        public int compareTo(Mono m) {
            return powers.toString().compareTo(m.powers.toString());
        }
    }

    private record Poly(SortedMap<Mono, Q> terms) {
        Poly {
            TreeMap<Mono, Q> r = new TreeMap<>(terms);
            r.values().removeIf(Q::zero);
            if (r.size() > 512) throw new IllegalArgumentException();
            terms = Collections.unmodifiableSortedMap(r);
        }

        static Poly scalar(Q q) {
            return new Poly(new TreeMap<>(Map.of(Mono.ONE, q)));
        }

        static Poly variable(String v) {
            return new Poly(new TreeMap<>(Map.of(new Mono(new TreeMap<>(Map.of(v, 1))), Q.ONE)));
        }

        boolean zero() {
            return terms.isEmpty();
        }

        Q constant() {
            return zero()
                    ? Q.ZERO
                    : terms.size() == 1 && terms.containsKey(Mono.ONE) ? terms.get(Mono.ONE) : null;
        }

        Poly add(Poly p) {
            TreeMap<Mono, Q> r = new TreeMap<>(terms);
            p.terms.forEach((k, v) -> r.merge(k, v, Q::add));
            return new Poly(r);
        }

        Poly neg() {
            TreeMap<Mono, Q> r = new TreeMap<>();
            terms.forEach((k, v) -> r.put(k, v.neg()));
            return new Poly(r);
        }

        Poly mul(Poly p) {
            TreeMap<Mono, Q> r = new TreeMap<>();
            if ((long) terms.size() * p.terms.size() > 65536) throw new IllegalArgumentException();
            terms.forEach((k, v) -> p.terms.forEach((l, w) -> r.merge(k.mul(l), v.mul(w), Q::add)));
            return new Poly(r);
        }

        Poly monic() {
            if (zero()) throw new ArithmeticException();
            Q lead = terms.get(terms.firstKey());
            TreeMap<Mono, Q> r = new TreeMap<>();
            terms.forEach((k, v) -> r.put(k, v.div(lead)));
            return new Poly(r);
        }
    }

    private record Expr(Poly n, Poly d, Set<Poly> domains) {
        Expr {
            if (d.zero()) throw new ArithmeticException();
            domains = Set.copyOf(domains);
        }

        static Expr scalar(Q q) {
            return new Expr(Poly.scalar(q), Poly.scalar(Q.ONE), Set.of());
        }

        Q constant() {
            Q a = n.constant(), b = d.constant();
            return a != null && b != null ? a.div(b) : null;
        }

        Set<Poly> union(Expr e) {
            Set<Poly> r = new HashSet<>(domains);
            r.addAll(e.domains);
            return r;
        }

        Expr add(Expr e) {
            return new Expr(n.mul(e.d).add(e.n.mul(d)), d.mul(e.d), union(e));
        }

        Expr neg() {
            return new Expr(n.neg(), d, domains);
        }

        Expr sub(Expr e) {
            return add(e.neg());
        }

        Expr mul(Expr e) {
            return new Expr(n.mul(e.n), d.mul(e.d), union(e));
        }

        Expr div(Expr e) {
            if (e.n.zero()) throw new ArithmeticException();
            Set<Poly> r = union(e);
            if (e.n.constant() == null) r.add(e.n.monic());
            return new Expr(n.mul(e.d), d.mul(e.n), r);
        }

        Expr pow(int exponent) {
            if (exponent == 0 && n.zero()) throw new IllegalArgumentException();
            if (Math.abs(exponent) > 20) throw new IllegalArgumentException();
            Expr base = exponent < 0 ? scalar(Q.ONE).div(this) : this, r = scalar(Q.ONE);
            for (int i = 0; i < Math.abs(exponent); i++) r = r.mul(base);
            return new Expr(r.n, r.d, union(r));
        }

        Comparison compare(Expr e) {
            if (!n.mul(e.d).equals(e.n.mul(d))) return Comparison.NOT_EQUIVALENT;
            return domains.equals(e.domains) ? Comparison.EQUIVALENT : Comparison.UNKNOWN;
        }
    }

    private static Expr parse(String s) {
        Parser p = new Parser(s.replace('{', '(').replace('}', ')'));
        Expr e = p.sum();
        if (p.i != p.s.length()) throw new IllegalArgumentException();
        return e;
    }

    private static final class Parser {
        final String s;
        int i, depth;

        Parser(String s) {
            if (s.length() > 1000) throw new IllegalArgumentException();
            this.s = s;
        }

        boolean take(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        Expr sum() {
            enter();
            try {
                Expr e = product();
                while (i < s.length()) {
                    if (take('+')) e = e.add(product());
                    else if (take('-')) e = e.sub(product());
                    else break;
                }
                return e;
            } finally {
                depth--;
            }
        }

        void enter() {
            if (++depth > 64) throw new IllegalArgumentException();
        }

        Expr product() {
            Expr e = unary();
            while (i < s.length()) {
                if (take('*')) e = e.mul(unary());
                else if (take('/')) e = e.div(unary());
                else if (s.charAt(i) == '(' || Character.isLetter(s.charAt(i))) e = e.mul(unary());
                else break;
            }
            return e;
        }

        Expr unary() {
            enter();
            try {
                if (take('+')) return unary();
                if (take('-')) return unary().neg();
                Expr e = atom();
                if (take('^')) {
                    Expr exp = unary();
                    Q q = exp.constant();
                    if (q == null || !q.d.equals(BigInteger.ONE))
                        throw new IllegalArgumentException();
                    e = e.pow(q.n.intValueExact());
                }
                return e;
            } finally {
                depth--;
            }
        }

        Expr atom() {
            if (take('(')) {
                Expr e = sum();
                if (!take(')')) throw new IllegalArgumentException();
                return e;
            }
            if (i < s.length() && s.charAt(i) < 128 && Character.isLetter(s.charAt(i))) {
                String v = s.substring(i, ++i);
                return new Expr(Poly.variable(v), Poly.scalar(Q.ONE), Set.of());
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (start == i) throw new IllegalArgumentException();
            BigDecimal n = new BigDecimal(s.substring(start, i));
            return Expr.scalar(new Q(n.unscaledValue(), BigInteger.TEN.pow(n.scale())));
        }
    }

    private MathAnswer() {}
}
