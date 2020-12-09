/*
 * themis - A fair search engine for scientific articles
 *
 * Currently over the Semantic Scholar Open Research Corpus
 * http://s2-public-api-prod.us-west-2.elasticbeanstalk.com/corpus/
 *
 * Collaborative work with the undergraduate/graduate students of
 * Information Retrieval Systems (hy463) course
 * Spring Semester 2020
 *
 * -- Writing code during COVID-19 pandemic times :-( --
 *
 * Aiming to participate in TREC 2020 Fair Ranking Track
 * https://fair-trec.github.io/
 *
 * Computer Science Department http://www.csd.uoc.gr
 * University of Crete
 * Greece
 *
 * LICENCE: TO BE ADDED
 *
 * Copyright 2020
 *
 */
package gr.csd.uoc.hy463.themis.lexicalAnalysis.collections.SemanticScholar;

import gr.csd.uoc.hy463.themis.config.Config;
import gr.csd.uoc.hy463.themis.utils.Pair;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Class responsible for reading textual entries from the json description of
 * entries
 *
 * @author Panagiotis Papadakos (papadako@ics.forth.gr)
 */
public class S2JsonEntryReader {

    private static final Logger __LOGGER__ = LogManager.getLogger(S2JsonEntryReader.class);

    
    public static S2TextualEntry readTextualEntry_QueryFile(String jsonToRead) {
    	S2TextualEntry entry = new S2TextualEntry();
        JSONParser parser = new JSONParser();
        
        try {
        	Object obj = parser.parse(jsonToRead);
        	
        	// This should be a JSON object.
            JSONObject jsonObject = (JSONObject) obj;
            
            //get the document ids
            JSONArray Document_Relevance = (JSONArray) jsonObject.get("documents");
            HashMap<String,Boolean> rel_docs=new HashMap<>();
            List<Pair<String,Boolean>> list_rel_docs=new ArrayList<>();
            if(Document_Relevance!=null)
            {
            	for(int i=0;i<Document_Relevance.size();i++)
            	{
            		JSONObject DocumentInfo = (JSONObject) Document_Relevance.get(i);
                    String DocumentID = (String) DocumentInfo.get("doc_id");
                    Long Doc_Relevance = (Long) DocumentInfo.get("relevance");
                    if(Doc_Relevance==1)
                    {
                    	rel_docs.put(DocumentID, true);
                    	list_rel_docs.add(new Pair(DocumentID,true));
                    }
                    else
                    {  
                    	rel_docs.put(DocumentID, false);
                    	list_rel_docs.add(new Pair(DocumentID,false));
                    }
            	}
            }
            entry.setDocumentRelevanceMap(rel_docs);
            entry.setListDocumentRelevanceMap(list_rel_docs);
            
 
            //get frequency
            Double frequency =(Double) jsonObject.get("frequency");
            entry.setFrequency(frequency);
            
            //get query id
            Long qid=(Long) jsonObject.get("qid");
            entry.setQueryID(qid);
            
            //get the Query
            String Query =(String) jsonObject.get("query");
            entry.setQuery(Query);
            
        } catch (ParseException e) {
            __LOGGER__.error(e.getMessage());
        }
    	
    	return entry;
    }
    
    
    // Method that reads all textual information from an entry
    public static S2TextualEntry readTextualEntry(String jsonToRead) {
        S2TextualEntry entry = new S2TextualEntry();
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(jsonToRead);
            
            // This should be a JSON object.
            JSONObject jsonObject = (JSONObject) obj;

            // Get the id for example
            String id = (String) jsonObject.get("id");
            entry.setId(id);

            // Get the title for example
            String titleCheck = (String) jsonObject.get("title");
            String title = titleCheck != null ? titleCheck : "";
            entry.setTitle(title);

            // Get abstract for example
            String paperAbstractCheck = (String) jsonObject.get("paperAbstract");
            String paperAbstract = paperAbstractCheck != null ? paperAbstractCheck : "";
            entry.setPaperAbstract(paperAbstract);

            // Read entities. A JSONArray
            JSONArray entitiesArray = (JSONArray) jsonObject.get("entities");
            List<String> entities = new ArrayList<>();
            if (entitiesArray != null) {
                entitiesArray.forEach(entity -> {
                    entities.add(entity.toString());
                });
            }
            entry.setEntities(entities);

            // Read fieldsOfStudy. A JSONArray
            JSONArray fieldsArray = (JSONArray) jsonObject.get("fieldsOfStudy");
            List<String> fields = new ArrayList<>();
            if (fieldsArray != null) {
                fieldsArray.forEach(field -> {
                    fields.add(field.toString());
                });
            }
            entry.setFieldsOfStudy(fields);

            // Read authors. A JSONArray
            JSONArray authorsList = (JSONArray) jsonObject.get("authors");
            List<Pair<String, List<String>>> authors = new ArrayList<>();
            if (authorsList != null) {
                for (int i = 0; i < authorsList.size(); i++) {
                    JSONObject authorInfo = (JSONObject) authorsList.get(i);
                    String authorName = (String) authorInfo.get("name");
                    // Now get all the ids
                    JSONArray idsList = (JSONArray) authorInfo.get("ids");
                    List<String> ids = new ArrayList<>();
                    if (idsList != null) {
                        for (int j = 0; j < idsList.size(); j++) {
                            String ID = (String) idsList.get(j);
                            ids.add(ID);
                        }
                    }
                    Pair author = new Pair(authorName, ids);
                    authors.add(author);
                }
            }
            entry.setAuthors(authors);

            // Read sources. A JSONArray
            JSONArray citationsArray = (JSONArray) jsonObject.get("outCitations");
            List<String> citations = new ArrayList<>();
            if (citationsArray != null) {
                citationsArray.forEach(citation -> {
                    citations.add(citation.toString());
                });
            }
            entry.setCitations(citations);

            // Get journal for example
            String journalCheck = (String) jsonObject.get("journalName");
            String journal = journalCheck != null ? journalCheck : "";
            entry.setJournalName(journal);

            // Read sources. A JSONArray
            JSONArray sourcesArray = (JSONArray) jsonObject.get("sources");
            List<String> sources = new ArrayList<>();
            if (sourcesArray != null) {
                sourcesArray.forEach(source -> {
                    sources.add(source.toString());
                });
            }
            entry.setSources(sources);

            // Get year for example
            Long yearLong = (Long) jsonObject.get("year");
            int year = yearLong != null ? yearLong.intValue() : 0;
            entry.setYear(year);

            // Get venue for example
            String venueCheck = (String) jsonObject.get("venue");
            String venue = venueCheck != null ? venueCheck : "";
            entry.setVenue(venue);

        } catch (ParseException e) {
            __LOGGER__.error(e.getMessage());
        }

        return entry;
    }

    // Method that reads all textual information from an entry
    public static S2GraphEntry readGraphEntry(String jsonToRead) {
        S2GraphEntry entry = new S2GraphEntry();
        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(jsonToRead);

            // This should be a JSON object.
            JSONObject jsonObject = (JSONObject) obj;

            // Get the id for example
            String id = (String) jsonObject.get("id");
            entry.setId(id);

            // Read authors. A JSONArray
            JSONArray authorsList = (JSONArray) jsonObject.get("authors");
            List<String> authors = new ArrayList<>();
            if (authorsList != null) {
                for (int i = 0; i < authorsList.size(); i++) {
                    JSONObject authorInfo = (JSONObject) authorsList.get(i);
                    // Now get all the ids
                    JSONArray idsList = (JSONArray) authorInfo.get("ids");
                    List<String> ids = new ArrayList<>();
                    if (idsList != null) {
                        for (int j = 0; j < idsList.size(); j++) {
                            String ID = (String) idsList.get(j);
                            authors.add(ID);
                        }
                    }
                }
            }
            entry.setAuthors(authors);

            // Read sources. A JSONArray
            JSONArray citationsArray = (JSONArray) jsonObject.get("outCitations");
            List<String> citations = new ArrayList<>();
            if (citationsArray != null) {
                citationsArray.forEach(citation -> {
                    citations.add(citation.toString());
                });
            }
            entry.setCitations(citations);

        } catch (ParseException e) {
            __LOGGER__.error(e.getMessage());
        }

        return entry;
    }

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        int pass = 0;
        // Stupid code to finish things quickly
        // Hope we have enough RAM
        Map<String, String> titles = new HashMap<>();
        while (pass < 2) {
            // Get all filenames in collection
            // This code was used to extract all titles in the collection
            String datasetPath = config.getDatasetPath();
            Set<String> result = Stream.of(new File(datasetPath).listFiles())
                    .filter(file -> !file.isDirectory())
                    .map(File::getName)
                    .collect(Collectors.toSet());

            String titlesDir = "/home/papadako/titles/";
            File dir = new File(titlesDir);
            if (!dir.exists()) {
                dir.mkdir();
            }

            // for each file name
            for (String file : result) {
                BufferedReader reader = new BufferedReader(new FileReader(datasetPath + file));
                String publication = reader.readLine();
                while (publication != null) {
                    S2TextualEntry entry = S2JsonEntryReader.readTextualEntry(publication);
                    String title = entry.getTitle();
                    String id = entry.getId();

                    // If first pass
                    if (pass == 0) {
                        // Add title to memory
                        titles.put(id, title);
                    } else {
                        // Create directory using the first 3 characters
                        // since we reach limits of common filesystem due to the huge
                        // number of files
                        File dirInternal = new File(titlesDir + id.substring(0, 3));
                        if (!dirInternal.exists()) {
                            dirInternal.mkdir();
                        }

                        FileWriter fileWriter = new FileWriter(dirInternal + "/" + id);
                        // Now write all
                        List<String> citations = entry.getCitations();
                        for (String citation : citations) {
                            String citationTitle = titles.get(citation);
                            if (citationTitle != null)
                                fileWriter.write(citationTitle + "\n");
                        }
                        fileWriter.close();
                    }
                    publication = reader.readLine();
                }
            }
            pass++;
        }

        String json = "{\n"
                + "	\"entities\": [],\n"
                + "	\"journalVolume\": \"\",\n"
                + "	\"journalPages\": \"\",\n"
                + "	\"pmid\": \"\",\n"
                + "	\"fieldsOfStudy\": [\"Physics\"],\n"
                + "	\"year\": 2015,\n"
                + "	\"outCitations\": [\"2497ed63572e8d5e5fe7945f0b23e0d090acd51c\", \"03b317054274da28acfb2c8e082f38d7dcfdce04\", \"070c58ff3d4f5ca3383c20a23af3594ae6e564ab\", \"f9a1951720cafa3706b341c0d14ddd57d9c83043\", \"26052227014c270c3f6013d98fdb8db1b80f8607\", \"8de63b8021633e45585874468ff5fe4bfe3ee476\", \"91d9b8d56ce67a90abfe0c9fc7483b8220ad3c66\", \"5baead167bceac9bdcbd7ac808620bb8987da323\", \"778f2e33cb7b0dfc0d3925df852fa4e576e75890\", \"88a11402d59f026ae5cd93f044e0c038f4373d51\", \"c2038b5d11a4dd9017d7a410b93f088f4dc8d1e4\", \"fa1aff91383e227fc115fc0621fd7452ebca46ab\"],\n"
                + "	\"s2Url\": \"https://semanticscholar.org/paper/1b2f4e5be76a0a746b72110b447b42fffa046b5c\",\n"
                + "	\"s2PdfUrl\": \"\",\n"
                + "	\"id\": \"1b2f4e5be76a0a746b72110b447b42fffa046b5c\",\n"
                + "	\"authors\": [{\n"
                + "		\"name\": \"Xiang Fa Liu\",\n"
                + "		\"ids\": [\"153201706\"]\n"
                + "	}, {\n"
                + "		\"name\": \"Guodong Xia\",\n"
                + "		\"ids\": [\"46932503\"]\n"
                + "	}, {\n"
                + "		\"name\": \"Guo-zhen Yang\",\n"
                + "		\"ids\": [\"50147063\"]\n"
                + "	}],\n"
                + "	\"journalName\": \"\",\n"
                + "	\"paperAbstract\": \"Abstract Experimental investigations on the characteristics of air–water two-phase flow in the vertical helical rectangular channel are performed using the high speed flow visualization. The flow pattern map and the transition in the helical rectangular channel are presented. The flow pattern evolution in different positions of the helical rectangular channel is illustrated. The discussion on the coalescence of the bubble and slug is presented. The slug velocity, slug length distribution, liquid slug frequency, falling liquid film velocity and falling film thickness along the slug are investigated. The dimensionless liquid film thickness of the annular flow on the outer side of the channel is measured using the digital image processing algorithm.\",\n"
                + "	\"inCitations\": [],\n"
                + "	\"pdfUrls\": [\"http://www.mechwork.ir/uploads/papers/4-Experimental%20study%20on%20the%20characteristics%20of%20air-water%20two-phase%20flow%20in%20vertical.pdf\"],\n"
                + "	\"title\": \"Experimental study on the characteristics of air–water two-phase flow in vertical helical rectangular channel\",\n"
                + "	\"doi\": \"10.1016/j.ijmultiphaseflow.2015.03.012\",\n"
                + "	\"sources\": [],\n"
                + "	\"doiUrl\": \"https://doi.org/10.1016/j.ijmultiphaseflow.2015.03.012\",\n"
                + "	\"venue\": \"\"\n"
                + "}";

        System.out.println(json);
        System.out.println(S2JsonEntryReader.readTextualEntry(json));
        System.out.println(S2JsonEntryReader.readGraphEntry(json));

    }

}
