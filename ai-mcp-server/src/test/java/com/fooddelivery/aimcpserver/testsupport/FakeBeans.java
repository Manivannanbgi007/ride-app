package com.fooddelivery.aimcpserver.testsupport;

import com.fooddelivery.aimcpserver.action.ActionStore;
import com.fooddelivery.aimcpserver.draft.DraftStore;
import com.fooddelivery.aimcpserver.proposal.ProposalStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** The model and Redis of the MOCKED context tests. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeBeans {

    @Bean
    @Primary
    public ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    public ProposalStore inMemoryProposalStore() {
        return new InMemoryProposalStore();
    }

    @Bean
    @Primary
    public DraftStore inMemoryDraftStore() {
        return new InMemoryDraftStore();
    }

    @Bean
    @Primary
    public ActionStore inMemoryActionStore() {
        return new InMemoryActionStore();
    }
}
