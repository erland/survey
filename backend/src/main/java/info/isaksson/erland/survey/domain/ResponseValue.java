package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "response_value")
public class ResponseValue extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "response_id") public Response response;
    @Column(name = "text_value", columnDefinition = "text") public String textValue;
    @Column(name = "numeric_value") public Integer numericValue;
    @Column(name = "boolean_value") public Boolean booleanValue;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "option_id") public SurveyRunOption option;
}
