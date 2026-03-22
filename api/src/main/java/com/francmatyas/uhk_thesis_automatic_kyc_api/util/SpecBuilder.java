package com.francmatyas.uhk_thesis_automatic_kyc_api.util;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public final class SpecBuilder {

    private SpecBuilder() {}
    public static <T> Specification<T> buildStringSearchSpec(
            String query,
            List<Column> columns
    ) {
        final String qs = (query == null) ? "" : query.trim().toLowerCase();
        final String like = "%" + qs + "%";

        return (root, cq, cb) -> {
            if (qs.isEmpty()) {
                return cb.conjunction();
            }

            var preds = columns.stream()
                    .filter(Column::isFilterable)
                    .map(col -> {
                        Path<?> path = root.get(col.getAccessorKey());

                        Expression<String> asString;
                        if (String.class.equals(path.getJavaType())) {
                            asString = path.as(String.class);
                        } else {
                            // použije Postgres CONCAT pro převedení libovolného typu na text
                            asString = cb.function(
                                    "concat",
                                    String.class,
                                    path,
                                    cb.literal("")
                            );
                        }

                        return cb.like(cb.lower(asString), like);
                    })
                    .toList();

            return preds.isEmpty()
                    ? cb.conjunction()
                    : cb.or(preds.toArray(new Predicate[0]));
        };
    }
}
