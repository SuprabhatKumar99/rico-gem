package com.ricozknow.aiplatform;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeGraphTest {
    @Test void identicalTermsArePerfectMatch(){
        assertEquals(1.0,KnowledgeGraphController.similarity(Set.of("alpha","bravo"),Set.of("bravo","alpha")));
    }
    @Test void disjointTermsCannotBeMerged(){
        assertEquals(0.0,KnowledgeGraphController.similarity(Set.of("alpha"),Set.of("bravo")));
    }
    @Test void ignoresStopWordsAndKeepsMeaningfulTokens(){
        var terms=KnowledgeGraphController.keywords("This article describes authentication, provisioning, and authentication.");
        assertTrue(terms.contains("authentication"));
        assertFalse(terms.contains("article"));
    }
}
