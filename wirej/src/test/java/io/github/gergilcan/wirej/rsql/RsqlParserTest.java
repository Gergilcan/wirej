package io.github.gergilcan.wirej.rsql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.exceptions.WireJException;

class RsqlParserTest {

    private static class AliasedEntity {
        @JsonAlias("full_name")
        private String name;
    }

    private final RsqlParser parser = new RsqlParser();

    private DatabaseStatement<?> statement;

    @BeforeEach
    void setUp() {
        statement = mock(DatabaseStatement.class);
        when(statement.getOriginalQuery()).thenReturn("SELECT * FROM users WHERE :filters");
    }

    @Test
    void equalsOperatorProducesEqualityClauseAndBindsValue() {
        String where = parser.parse("name==John", User.class, statement);

        assertThat(where).isEqualTo("name = :filter_value_1");
        verify(statement).setParameter("filter_value_1", "John");
    }

    @Test
    void comparisonOperatorsProduceCorrectSql() {
        assertThat(parser.parse("id>5", User.class, statement)).isEqualTo("id > :filter_value_1");
        assertThat(parser.parse("id>=5", User.class, statement)).isEqualTo("id >= :filter_value_1");
        assertThat(parser.parse("id<5", User.class, statement)).isEqualTo("id < :filter_value_1");
        assertThat(parser.parse("id<=5", User.class, statement)).isEqualTo("id <= :filter_value_1");
    }

    @Test
    void notEqualOperatorProducesCorrectSql() {
        String where = parser.parse("name!=John", User.class, statement);

        assertThat(where).isEqualTo("name != :filter_value_1");
        verify(statement).setParameter("filter_value_1", "John");
    }

    @Test
    void inOperatorProducesLikeClauseWithWildcardWrappedValue() {
        String where = parser.parse("name=in=John", User.class, statement);

        assertThat(where).isEqualTo("name LIKE :filter_value_1");
        verify(statement).setParameter("filter_value_1", "%John%");
    }

    @Test
    void notInOperatorProducesNotLikeClauseWithWildcardWrappedValue() {
        String where = parser.parse("name=out=John", User.class, statement);

        assertThat(where).isEqualTo("name NOT LIKE :filter_value_1");
        verify(statement).setParameter("filter_value_1", "%John%");
    }

    @Test
    void semicolonSeparatedClausesAreCombinedWithAnd() {
        String where = parser.parse("id==1;name==John", User.class, statement);

        assertThat(where).isEqualTo("(id = :filter_value_1 AND name = :filter_value_2)");
    }

    @Test
    void commaSeparatedClausesAreCombinedWithOr() {
        String where = parser.parse("id==1,id==2", User.class, statement);

        assertThat(where).isEqualTo("(id = :filter_value_1 OR id = :filter_value_2)");
    }

    @Test
    void parenthesizedGroupsAreRespectedRatherThanFlattened() {
        String where = parser.parse("(id==1;name==John),id==2", User.class, statement);

        assertThat(where).isEqualTo(
                "((id = :filter_value_1 AND name = :filter_value_2) OR id = :filter_value_3)");
    }

    @Test
    void unparenthesizedMixedFiltersFollowStandardRsqlPrecedenceAndBindsTighterThanOr() {
        String where = parser.parse("id==1,name==John;id==2", User.class, statement);

        assertThat(where).isEqualTo(
                "(id = :filter_value_1 OR (name = :filter_value_2 AND id = :filter_value_3))");
    }

    @Test
    void dateValuedClauseIsWrappedWithDateFunction() {
        String where = parser.parse("id==2024-01-15", User.class, statement);

        assertThat(where).isEqualTo("DATE(id) = :filter_value_1");
        verify(statement).setParameter(eq("filter_value_1"), any(Timestamp.class));
    }

    @Test
    void jsonAliasIsUsedAsTheColumnNameWhenPresent() {
        String where = parser.parse("name==John", AliasedEntity.class, statement);

        assertThat(where).isEqualTo("full_name = :filter_value_1");
    }

    @Test
    void unrecognizedOperatorThrowsInsteadOfSilentlyDroppingTheClause() {
        WireJException ex = assertThrows(WireJException.class,
                () -> parser.parse("name~~~bogus~~~operator", User.class, statement));

        assertThat(ex.getMessage()).contains("Unrecognized filter clause").contains("name~~~bogus~~~operator");
    }

    @Test
    void ascendingAndDescendingSortProduceOrderByClause() {
        assertThat(parser.parseSorting("id==ASC", User.class)).isEqualTo("ORDER BY id ASC");
        assertThat(parser.parseSorting("id==DESC", User.class)).isEqualTo("ORDER BY id DESC");
    }

    @Test
    void multipleSortClausesAreCombinedWithComma() {
        assertThat(parser.parseSorting("name==ASC;id==DESC", User.class))
                .isEqualTo("ORDER BY name ASC, id DESC");
    }

    @Test
    void invalidSortDirectionThrows() {
        WireJException ex = assertThrows(WireJException.class,
                () -> parser.parseSorting("id==SIDEWAYS", User.class));

        assertThat(ex.getMessage()).contains("Invalid sort direction").contains("SIDEWAYS");
    }

    @Test
    void unrecognizedSortOperatorThrows() {
        assertThrows(WireJException.class, () -> parser.parseSorting("id~~~ASC", User.class));
    }
}
