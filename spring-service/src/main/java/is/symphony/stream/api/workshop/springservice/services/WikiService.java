package is.symphony.stream.api.workshop.springservice.services;

import is.symphony.stream.api.workshop.springservice.model.WikiRecord;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.util.regex.Pattern;

@Service
public class WikiService {

    private final WebClient wikiClient;

    private final Sinks.Many<WikiRecord> records = Sinks.many().replay().limit(100);

    private final static String URL = "https://stream.wikimedia.org/v2/stream";

    public WikiService() {
        this.wikiClient = WebClient.builder().baseUrl(URL).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        getEvents().filter(event -> event.getWiki() != null && event.getTitle() != null)
                .doOnNext(records::tryEmitNext).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    public Flux<String> getWikis() {
        return records.asFlux().map(WikiRecord::getWiki).distinct();
    }

    public Flux<String> getTitles(String wiki) {
        return records.asFlux().filter(wikiRecord -> wikiRecord.getWiki().equals(wiki)).map(WikiRecord::getTitle);
    }

    public Flux<String> getTitles() {
        return records.asFlux().map(WikiRecord::getTitle);
    }

    public Flux<String> getTitlesFiltered(String word) {
        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE);

        return records.asFlux().filter(record -> pattern.matcher(record.getTitle()).find()).map(WikiRecord::getTitle);
    }

    public Flux<WikiRecord> getEvents() {
        return wikiClient.get().uri("/mediawiki.recentchange")
                .retrieve().bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<WikiRecord>>() {})
                .mapNotNull(ServerSentEvent::data).retryWhen(Retry.indefinitely());
    }
}