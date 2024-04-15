package com.botdarr.api.sonarr;

import com.botdarr.api.ContentType;
import com.botdarr.commands.*;
import com.botdarr.commands.responses.CommandResponse;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SonarrCommands {
  private static boolean tryParseInt(String value) {  
    try {  
       Integer.parseInt(value);  
       return true;  
    } catch (NumberFormatException e) {  
       return false;  
    }  
 }

  private static String[] parseShowCommand(String command) {
    String title = null;
    String id = null;

    // Pattern for command type 1 and 2: "SHOW_TITLE_HERE SHOW_ID_HERE"
    Matcher matcher1 = Pattern.compile("(.*\\D)(\\d+)$").matcher(command);

    if (matcher1.find()) {
      title = matcher1.group(1).trim();
      id = matcher1.group(2);
    } else {
      // Pattern for command type 3: "SHOW_ID_HERE"
      Matcher matcher2 = Pattern.compile("^\\d+$").matcher(command);

      // TVDB
      if (matcher2.find()) {
        id = command;
      } else {
        // Command type 2: "SHOW_TITLE_HERE"
        title = command;
      }
    }

    return new String[] {title, id};
  }

  public static List<Command> getCommands(SonarrApi sonarrApi) {
    return new ArrayList<Command>() {{
      add(new BaseCommand(
              "show id add",
        "Adds a show using search text and tmdb id (i.e., show id add 30 clock 484767)",
              Arrays.asList("show-title", "show-tvdbid")) {
        @Override
        public List<CommandResponse> execute(String command) {
          int lastSpace = command.lastIndexOf(" ");
          if (lastSpace == -1) {
            throw new RuntimeException("Missing expected arguments - usage: show id add SHOW_TITLE_HERE SHOW_ID_HERE");
          }
          String searchText = command.substring(0, lastSpace);
          String id = command.substring(lastSpace + 1);
          validateShowTitle(searchText);
          validateShowId(id);
          return Collections.singletonList(sonarrApi.addWithIdAndTitle(searchText, id));
        }
      });
      add(new BaseCommand(
        "serie:",
        "Searches (i.e., \"serie: Game of Thrones\") or adds (i.e., \\\"serie: 121361\\\") a show.",
        Arrays.asList("show-title", "show-tvdbid")) {
        @Override
        public List<CommandResponse> execute(String command) {
          String[] showTitleAndId = parseShowCommand(command);
          String showTitle = showTitleAndId[0];
          String showId = showTitleAndId[1];

          // Get show by id only
          // serie: 114801
          if (showTitle == null && tryParseInt(showId)) {
            return Collections.singletonList(sonarrApi.addWithId(showId));
          }
          // Get show by title only
          // serie: Fairy Tail
          else if(showTitle != null && showId == null) {
            return sonarrApi.addWithTitle(showTitle);
          }
          // Get show by id and title
          // serie: Fairy Tail 114801
          else if (showTitle != null && tryParseInt(showId)) {
            return Collections.singletonList(sonarrApi.addWithIdAndTitle(showTitle, showId));
          } else {
            throw new RuntimeException("Missing expected arguments - usage: show id add SHOW_TITLE_HERE SHOW_ID_HERE");
          }
        }
      });
      add(new BaseCommand(
              "show title add",
        "Adds a show with just a title.",
              Collections.singletonList("show-title")) {
        @Override
        public List<CommandResponse> execute(String command) {
          validateShowTitle(command);
          return sonarrApi.addWithTitle(command);
        }
      });
      add(new BaseCommand("show downloads", "Shows all the active shows downloading in sonarr") {
        @Override
        public boolean hasArguments() {
          return false;
        }

        @Override
        public List<CommandResponse> execute(String command) {
          return new CommandResponseUtil().addEmptyDownloadsMessage(sonarrApi.downloads(), ContentType.SHOW);
        }
      });
      add(new BaseCommand("show profiles", "Displays all the profiles available to search for shows under (i.e., show add ANY)") {
        @Override
        public boolean hasArguments() {
          return false;
        }

        @Override
        public List<CommandResponse> execute(String command) {
          return sonarrApi.getProfiles();
        }
      });
      add(new BaseCommand(
              "show find existing",
              "Finds a existing show using sonarr (i.e., show find existing Ahh! Real fudgecakes)",
              Collections.singletonList("show-title")) {
        @Override
        public List<CommandResponse> execute(String command) {
          validateShowTitle(command);
          return sonarrApi.lookup(command, false);
        }
      });
      add(new BaseCommand(
              "show find new",
              "Finds a new show using sonarr (i.e., show find new Fresh Prince of Fresh air)",
              Collections.singletonList("show-title")) {
        @Override
        public List<CommandResponse> execute(String command) {
          validateShowTitle(command);
          return sonarrApi.lookup(command, true);
        }
      });
    }};
  }

  public static String getAddShowCommandStr(String title, long tvdbId) {
    String commandPrefix = CommandContext.getConfig().getPrefix();
    String firstCommand = commandPrefix + "show id add " + title + " " + tvdbId;
    String secondCommand = commandPrefix + "serie: " + tvdbId;

    return firstCommand + "\nor\n" + secondCommand;
  }

  public static String getHelpShowCommandStr() {
    return CommandContext.getConfig().getPrefix() + "shows help";
  }

  private static void validateShowTitle(String movieTitle) {
    if (Strings.isEmpty(movieTitle)) {
      throw new IllegalArgumentException("Show title is missing");
    }
  }

  private static void validateShowId(String id) {
    if (Strings.isEmpty(id)) {
      throw new IllegalArgumentException("Show id is missing");
    }
    try {
      Integer.valueOf(id);
    } catch (NumberFormatException e) {
      throw new RuntimeException("Show id is not a number");
    }
  }
}