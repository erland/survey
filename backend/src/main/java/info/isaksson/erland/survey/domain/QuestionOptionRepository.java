package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class QuestionOptionRepository implements PanacheRepositoryBase<QuestionOption, UUID> { }
