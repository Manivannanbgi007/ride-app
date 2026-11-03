package com.fooddelivery.reviewservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What a visitor sees of a customer's name. */
class ReviewerNamesTest {

    @Test
    void firstNameAndInitialOfTheLastName() {
        assertThat(ReviewerNames.mask("Priya Sharma")).isEqualTo("Priya S.");
        assertThat(ReviewerNames.mask("Rahul Kumar Verma")).isEqualTo("Rahul V.");
    }

    @Test
    void aSingleWordNameStaysAsItIs() {
        assertThat(ReviewerNames.mask("Madonna")).isEqualTo("Madonna");
        assertThat(ReviewerNames.mask("  Madonna  ")).isEqualTo("Madonna");
    }

    @Test
    void extraSpacesAndLowerCaseInitialsAreHandled() {
        assertThat(ReviewerNames.mask("  priya \t  sharma ")).isEqualTo("priya S.");
        assertThat(ReviewerNames.mask("Priya   Sharma")).isEqualTo("Priya S.");
    }

    @Test
    void theInitialIsACompleteCharacter() {
        String accented = new String(Character.toChars(0xC9));
        assertThat(ReviewerNames.mask("Ana " + accented + "mile")).isEqualTo("Ana " + accented + ".");
        // A letter outside the basic plane is one code point (two chars): it is not cut in half
        String script = new String(Character.toChars(0x1D49C));
        assertThat(ReviewerNames.mask("Ana " + script + "b")).isEqualTo("Ana " + script + ".");
    }

    @Test
    void aMissingOrBlankNameBecomesCustomer() {
        assertThat(ReviewerNames.mask(null)).isEqualTo("Customer");
        assertThat(ReviewerNames.mask("")).isEqualTo("Customer");
        assertThat(ReviewerNames.mask("   ")).isEqualTo("Customer");
    }

    @Test
    void theLastNameIsNeverShown() {
        assertThat(ReviewerNames.mask("Priya Sharma")).doesNotContain("harma");
    }
}
