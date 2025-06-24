package icu.neurospicy.fibi.service;

import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
public class UserRepository {

    private final Map<String, User> users = new ConcurrentHashMap<>(Map.of("Fibi", new User("Fibi", "+1337", UUID.randomUUID().toString())));
    private String currentUserName;

    /**
     * Creates a new user with the given username (and random phone number)
     * and sets them as the current user in this repository.
     */
    public User newUser() {
        String username;
        do {
            username = NAMES.get(new Random().nextInt(NAMES.size()));
        }while (users.containsKey(username));
        String generatedNumber = "+" + Math.abs(new Random().nextLong(99999, 99999999));
        String generatedUuid = UUID.randomUUID().toString();
        User user = new User(username, generatedNumber, generatedUuid);
        users.put(username, user);
        this.currentUserName = username;
        return user;
    }

    /**
     * Retrieves a user by name. Returns null if not found.
     */
    public User getUserByName(String username) {
        return users.get(username);
    }

    /**
     * Switches the "current user" context to the given username
     * (assuming user already exists in the map).
     */
    public void setCurrentUser(String username) {
        if (users.containsKey(username)) {
            this.currentUserName = username;
        }
    }

    /**
     * Returns the current user context, or null if no user is set.
     */
    public User getCurrentUser() {
        if (currentUserName == null) return null;
        return users.get(currentUserName);
    }

    public User getUserByNumber(String number) {
        return users.values().stream().filter(u -> u.number.contains(number)).findFirst().orElseThrow();
    }

    public User getUserByUuid(String uuid) {
        return users.values().stream().filter(u -> uuid.equals(u.uuid)).findFirst().orElseThrow();
    }

    private void updateAndSave(String username, Function<User, User> update) {
        User user = getUserByName(username);
        User updatedUser = update.apply(user);
        users.remove(user.username);
        users.put(updatedUser.username, updatedUser);
    }

    public void saveCalDavInfo(String username, String url, String calUser, String password) {
        updateAndSave(username, (user) -> new User(user.username, user.number, user.uuid, url, calUser, password, user.wakeUpTime));
    }

    public void saveWakeUpTime(String username, LocalTime wakeUpTime) {
        updateAndSave(username, (user) -> new User(user.username, user.number, user.uuid, user.calDavUrl, user.calDavUsername, user.calDavPassword, wakeUpTime));
    }


    public static class User {
        public final String username;
        public final String number;
        public final String uuid;
        public final short deviceId = 1;
        public final String calDavUrl;
        public final String calDavUsername;
        public final String calDavPassword;
        public final LocalTime wakeUpTime;

        public User(String username, String number, String uuid) {
            this.username = username;
            this.number = number;
            this.uuid = uuid;
            this.calDavUrl = null;
            this.calDavUsername = null;
            this.calDavPassword = null;
            this.wakeUpTime = null;
        }

        public User(String username, String number, String uuid, String calDavUrl, String calDavUsername, String calDavPassword, LocalTime wakeUpTime) {
            this.username = username;
            this.number = number;
            this.uuid = uuid;
            this.calDavUrl = calDavUrl;
            this.calDavUsername = calDavUsername;
            this.calDavPassword = calDavPassword;
            this.wakeUpTime = wakeUpTime;
        }
    }

    private static final List<String> NAMES = Arrays.asList("Noah","James","Evelyn","Michael","Asher","Harper","Luca","Logan","Dylan","Avery","Mason","Julian","Maverick","Aiden","Miles","Grayson","Carter","Wyatt","Riley","Parker","Aria","Jayden","Cameron","Cooper","Rowan","Angel","Kai","Madison","Zoe","Nolan","Adrian","River","Brooks","Ryan","Charlie","Jordan","Christian","Theo","Beau","Walker","Micah","Eli","Sawyer","Genesis","Paisley","Landon","Easton","Gael","Addison","Quinn","Hunter","Emery","Amari","Carson","Atlas","Damian","Claire","Kinsley","Kennedy","Everly","Emmett","Hayden","Emerson","Ryder","Zion","Jade","Brooklyn","Archer","Tatum","Vivian","Hailey","Blake","Aubrey","Sage","Oakley","Skylar","Evan","Allison","Elliott","Legend","Juan","Remi","Ashton","Rory","Rylee","Peyton","Chase","Eva","Jude","Cole","Eliza","Elliot","Hadley","Alaia","Finley","Max","Ashley","Jayce","Dakota","Tyler","Sutton","Kaiden","Camden","Phoenix","Reese","Arya","Bentley","Maxwell","Stetson","Ryker","Milan","Beckett","Ayden","Tate","Shiloh","Remington","Sloane","Alex","Karter","Jesse","Blakely","Beckham","Dallas","Reagan","Bailey","Hayes","Mackenzie","Jett","Barrett","Morgan","June","Andrea","Taylor","Ariel","Ari","Presley","Lennon","Ellis","Aspen","Israel","Kyrie","Hallie","Nico","Armani","Elian","Haven","Saylor","Harlow","Cohen","Bryce","Paxton","Tristan","Palmer","Blair","Kimberly","Alexis","Delaney","Harmony","Stevie","Casey","Jordyn","Remy","Marley","Onyx","Cade","Ryleigh","London","Reign","Holden","Baylor","Salem","Camille","Hendrix","Londyn","Elise","Kendall","Spencer","Ali","Teagan","Emory","Anderson","Cody","Aidan","Payton","Harley","Rylan","Leighton","Brady","Andre","Cataleya","Niko","Callan","Gideon","Karson","Brooke","Colter","Cristian","Journey","Selah","Banks","Evelynn","Bradley","Briar","Jaden","Kylian","Kade","Esme","Mckenna","Sunny","Drew","Amiri","Lauren","Jocelyn","Raven","Winter","Sevyn","Miller","Kameron","Paige","Reece","Noel","Legacy","Jay","Kalani","Skyler","Bodie","Murphy","Koda","Jamie","Brooklynn","Memphis","Grady","Kylo","Oaklee","Demi","Kyle","Rowen","Kamryn","Saige","Raiden","Frankie","Francis","Carmen","Frances","Devin","Skye","Zariah","Reed","Raegan","Brynn","Sean","Royce","Jalen","Kayce","Zayne","Callen","Mallory","Mckenzie","Monroe","Cali","Gunner","Imani","Camryn","Corbin","Chandler","Layne","Paris","Miley","Bellamy","Robin","Asa","Cassidy","Tru","Alma","Quincy","Bo","Alison","Karsyn","Lorelai","Makenna","Halo","Andy","Maddison","Brinley","McKinley","Opal","Jaiden","Rio","Faye","Ivory","Allie","Amani","Ayaan","Meredith","Blaire","Kason","Bristol","Leland","Kylan","Royalty","Kora","Makenzie","Averie","Layton","Deacon","Scottie","Dior","Finnley","Alia","Jamari","Santana","Emerie","Jaime","Shane","Kenzie","Sasha","Madden","Kyla","Lian","Colby","Cleo","Ainsley","Sloan","Justice","Kasen","Shelby","Charleigh","Winnie","Jessie","Charley","Leslie","Dorian","Scout","Abby","Augustine","Scott","Yael","Harlan","Sky","Kyro","Whitley","Kelly","Kelsey","Marie","Arden","Fallon","Sol","Corey","Henley","Raylan","Novah","Jamison","Adley","True","Aries","Campbell","Ira","Khari","Halle","Haley","Macy","Joey","Promise","Sam","Amos","Rhodes","Harlem","Kyree","Artemis","Andi","Trace","Kacey","Noor","Lee","Khai","Chelsea","Dani","Amias","Loyal","Dillon","Shea","Alisson","Rayne","Zyair","Dustin","Angie","Azael","Hollis","Dutton","Seven","Kori","Lacey","Marlowe","Indy","Landry","Indigo","Cheyenne","Azaria","Ray","Elisha","Jaycee","Blaze","Lea","Flynn","Kodi","Rey","Shay","Chris","Elia","Shai","Quentin","Ever","Ty","Ashlyn","Maddie","Grey","Belen","Jrue","Ripley","Nori","Blessing","Bowie","Landyn","Rowyn","Billie","Kit","Austyn","Ellison","Noe","Gatlin","Aden","Kannon","Brodie","Ensley","Ollie","Waverly","Andie","Devon","Baylee","Kairi","Zen","Hadlee","Harlee","Keaton","Jaylin","Akira","Rohan","Keily","Egypt","Simone","Eddie","Zyon","Sidney","Marleigh","Ren","Dana","Kylen","Montana","Jerry","Kasey","Bailee","Ronnie","Teo","Whitney","Guadalupe","Kassidy","Devyn","Jericho","Lux","Hayley","Darcy","Kaya","Eiden","Kellen","Sailor","Rain","Ryann","Kartier","Clare","Perry","Kellan","Curtis","Foster","Toby","Hadleigh","Isa","Aarya","Eren","Laken","Averi","Wrenleigh","Moriah","Laramie","Carsyn","Navi","Randy","Joyce","Mattie","Storm");
}
