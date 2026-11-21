package com.ricozknow.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.analytics.AnalyticsDashboardService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGapScheduler {

    private final TenantRepository tenantRepository;
    private final AnalyticsDashboardService analytics;
    private final KnowledgeIntelligenceService intelligence;
    private final AiKnowledgeService ai;
    private final KnowledgeGapTicketRepository repository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 45 3 * * *")
    public void refresh() {

        tenantRepository.findAll().forEach(tenant -> {

            try {
                TenantContext.set(tenant.getId());

                analytics.knowledgeGaps(30, 50).forEach(g -> {

                    KnowledgeGapTicket ticket =
                            intelligence.upsertGap(
                                    g.query(),
                                    g.volumeCount(),
                                    g.zeroResultCount(),
                                    g.unsuccessfulCount(),
                                    g.gapScore()
                            );

                    /*
                     * Generate an AI suggestion only when
                     * one doesn't already exist.
                     */
                    if (ticket.getSuggestedTitle() == null
                            || ticket.getSuggestedTitle().isBlank()) {

                        try {

                            var generated =
                                    ai.suggestForGap(
                                            ticket.getQueryText()
                                    );

                            if (generated != null) {

                                ticket.setSuggestedTitle(
                                        generated.title()
                                );

                                if (generated.outline() != null) {
                                    ticket.setSuggestedOutline(
                                            objectMapper.writeValueAsString(
                                                    generated.outline()
                                            )
                                    );
                                }

                                if (generated.keywords() != null) {
                                    ticket.setSuggestedKeywords(
                                            objectMapper.writeValueAsString(
                                                    generated.keywords()
                                            )
                                    );
                                }

                                repository.save(ticket);
                            }

                        } catch (Exception ex) {

                            /*
                             * AI failure should not prevent
                             * knowledge-gap tickets from being created.
                             */
                            log.warn(
                                    "AI suggestion generation failed for knowledge gap '{}'",
                                    ticket.getQueryText(),
                                    ex
                            );
                        }
                    }
                });

            } catch (Exception ex) {

                log.warn(
                        "Knowledge gap refresh failed for tenant {}",
                        tenant.getId(),
                        ex
                );

            } finally {
                TenantContext.clear();
            }
        });
    }
}