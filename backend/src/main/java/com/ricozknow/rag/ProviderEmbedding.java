package com.ricozknow.rag;

import java.util.List;

/** Provider-neutral embedding adapter. */
public interface ProviderEmbedding {
    String providerId();
    List<Float> embed(String text);
}
