package info.isaksson.erland.survey.runapi;

public final class RunSummaryDtos {
    private RunSummaryDtos() {}

    public record LiveSummary(long started, long active, long submitted) {}
}
