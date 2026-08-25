package org.example.ai.embedding;

/**
 * Renders a {@code float[]} into pgvector's text literal form {@code [0.1,0.2,...]}. The result is
 * only ever bound as a {@code ?} parameter to {@code CAST(? AS vector)} — never string-concatenated
 * into SQL (PLAN.md §7 item 5). {@link Float#toString(float)} is locale-independent (always a '.'
 * decimal point) and round-trippable; pgvector's parser accepts the scientific notation it can emit
 * for very small magnitudes.
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    public static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Float.toString(vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
