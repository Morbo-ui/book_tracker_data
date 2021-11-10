package ru.morboui.booktrackerdataloader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.morboui.booktrackerdataloader.author.*;
import ru.morboui.booktrackerdataloader.book.Book;
import ru.morboui.booktrackerdataloader.book.BookRepository;
import ru.morboui.booktrackerdataloader.connection.DataStaxAstraProperties;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)

public class BookTrackerDataLoaderApplication {

    @Autowired
    AuthorRepository authorRepository;

    @Autowired
    BookRepository bookRepository;

    @Value("${datadump.location.author}")
    private String authorDumpLocation;

    @Value("${datadump.location.works}")
    private String worksDumpLocation;

    public static void main(String[] args) {
        SpringApplication.run(BookTrackerDataLoaderApplication.class, args);
    }

    private void initAuthors() {
        Path path = Paths.get(authorDumpLocation);
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                String jsonString = line.substring(line.indexOf("{"));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);

                    //Construct Author object
                    Author author = new Author();
                    author.setName(jsonObject.optString("name"));
                    author.setPersonalName(jsonObject.optString("personal_name"));
                    author.setId(jsonObject.optString("key").replace(("/authors/"), ""));

                    //Persist using Repo
                    System.out.println("Saving author " + author.getName());
                    authorRepository.save(author);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initWorks() throws IOException {
        Path path = Paths.get(worksDumpLocation);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                String jsonString = line.substring(line.indexOf("{"));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    //Construct Book object
                    Book book = new Book();
                    book.setId(jsonObject.getString("key").replace("/works/", ""));
                    book.setName(jsonObject.optString("title"));
                    JSONObject descriptionObject = jsonObject.optJSONObject("description");
                    if (descriptionObject != null) {
                        book.setDescription(descriptionObject.optString("value"));
                    }
                    JSONObject publishedObj = jsonObject.optJSONObject("created");
                    if (publishedObj != null) {
                        String dateStr = publishedObj.optString("value");
                        book.setPublishedDate(LocalDate.parse(dateStr, format));
                    }

                    JSONArray coversJSONArr = jsonObject.optJSONArray("covers");
                    if (coversJSONArr != null) {
                        List<String> coverIds = new ArrayList<>();
                        for (int i = 0; i < coversJSONArr.length(); i++) {
                            coverIds.add(coversJSONArr.getString(i));
                        }
                        book.setCoverIds(coverIds);
                    }

                    JSONArray auhorsJSONArr = jsonObject.optJSONArray("authors");
                    if (auhorsJSONArr != null) {
                        List<String> authorIds = new ArrayList<>();
                        for (int i = 0; i < auhorsJSONArr.length(); i++) {
                            String authorId = auhorsJSONArr.getJSONObject(i).getJSONObject("author").getString("key")
                                    .replace("/authors/", "");
                            authorIds.add(authorId);
                        }
                        book.setAuthorId(authorIds);
                        authorIds.stream().map(id -> authorRepository.findById(id))
                                .map(optionalAuthor -> {
                                    if (!optionalAuthor.isPresent()) return "Unknown author";
                                    return optionalAuthor.get().getName();
                                }).collect(Collectors.toList());
                        book.setAuthorNames(authorIds);
                        //Persist using Repo
                        System.out.println("Saving book " + book.getName());
                        bookRepository.save(book);
                    }


                } catch (JSONException e) {
                    e.printStackTrace();
                }

            });
        }
    }

    @PostConstruct
    public void start() throws IOException {
        initAuthors();
        initWorks();
        System.out.println(authorDumpLocation);

    }


    @Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
        Path bundle = astraProperties.getSecureConnectBundle().toPath();
        return builder -> builder.withCloudSecureConnectBundle(bundle);
    }
}

