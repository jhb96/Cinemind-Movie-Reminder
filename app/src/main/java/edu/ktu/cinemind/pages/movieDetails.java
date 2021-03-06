package edu.ktu.cinemind.pages;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import edu.ktu.cinemind.adapters.ViewPagerAdapter;
import edu.ktu.cinemind.adapters.recommendedMovieAdapter;
import edu.ktu.cinemind.requestOperators.movieRequestOperator;
import edu.ktu.cinemind.services.AlarmReceiver;
import edu.ktu.cinemind.R;
import edu.ktu.cinemind.adapters.castListAdapter;
import edu.ktu.cinemind.adapters.crewListAdapter;
import edu.ktu.cinemind.requestOperators.movieDetailsRequestOperator;
import edu.ktu.cinemind.objects.cast;
import edu.ktu.cinemind.objects.crew;
import edu.ktu.cinemind.objects.movieObj;
import edu.ktu.cinemind.objects.movieToSave;
import edu.ktu.cinemind.services.RecyclerItemClickListener;


public class movieDetails extends AppCompatActivity implements movieDetailsRequestOperator.RequestOperatorListener, YouTubePlayer.OnInitializedListener, movieRequestOperator.RequestOperatorListener{

    private CheckBox addFavorites,addWatchlist,setReminder;
    private ImageView moviePoster;
    private TextView movieName,movieDirector,movieLength,movieRate,movieReleaseDate, movieOverview, movieGenres;
    private movieObj publication;

    private ListView castLv,crewLv;
    private castListAdapter castAdapter;
    private crewListAdapter crewAdapter;
    private List<crew> crewList=new ArrayList<>();
    private List<cast> castList=new ArrayList<>();

    private List<movieToSave> favoriteMovies=new ArrayList<>();
    private List<movieToSave> watchlistMovies=new ArrayList<>();
    private List<movieToSave> reminderMovies=new ArrayList<>();

    private DatabaseReference dbFavorites,dbWatchlist,dbReminder;
    private FirebaseAuth firebaseAuth;

    private boolean alreadyinFavs,alreadyinWatch,alreadyinReminder;

    PendingIntent pendingIntent;
    AlarmManager alarmManager;
    Intent alarmIntent;

    private ShareActionProvider mShareActionProvider;

    private ViewPager viewPager;
    private ViewPagerAdapter viewPagerAdapter;

    private Button watchTrailer;

    private RecyclerView recyclerView;
    private List<movieObj> recMovies=new ArrayList<>();
    private List<movieObj> publications = new ArrayList<>();
    private recommendedMovieAdapter recMoviesAdapter;

    private movieObj movieWithDetails;

    private boolean gotDetails=false,gotRecommendedMovies=false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        recMovies.removeAll(recMovies);

        sendRequest();

        setContentView(R.layout.moviedetailsdesign);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        firebaseAuth=FirebaseAuth.getInstance();


        dbFavorites= FirebaseDatabase.getInstance().getReference("Favorites");
        dbWatchlist= FirebaseDatabase.getInstance().getReference("Watchlist");
        dbReminder=FirebaseDatabase.getInstance().getReference("Reminder");

        if (getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        addFavorites=(CheckBox) findViewById(R.id.addFavoritesButton);
        addWatchlist=(CheckBox) findViewById(R.id.addWatchlistButton);
        setReminder=(CheckBox) findViewById(R.id.setReminderButton);

        moviePoster=(ImageView) findViewById(R.id.movieDetailPoster);

        movieName=(TextView) findViewById(R.id.movieDetailTitle);
        movieDirector=(TextView) findViewById(R.id.movieDetailDirector);
        movieReleaseDate=(TextView) findViewById(R.id.movieDetailReleaseDate);
        movieLength=(TextView) findViewById(R.id.movieDetailLength);
        movieRate=(TextView) findViewById(R.id.movieDetailRate);
        movieOverview=(TextView) findViewById(R.id.movieOverview);
        movieGenres=(TextView) findViewById(R.id.movieDetailGenres);

        castLv=(ListView) findViewById(R.id.castListView);
        crewLv=(ListView) findViewById(R.id.crewListView);

        watchTrailer=(Button) findViewById(R.id.watchTrailer);

        watchTrailer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                watchYoutubeVideo(publication.getVideoList().get(0).getKey());
            }
        });

        castAdapter=new castListAdapter(this,castList);
        crewAdapter=new crewListAdapter(this,crewList);

        castLv.setAdapter(castAdapter);
        crewLv.setAdapter(crewAdapter);

        recyclerView = (RecyclerView) findViewById(R.id.myRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.HORIZONTAL, false));
        recMoviesAdapter=new recommendedMovieAdapter(recMovies);
        recyclerView.setAdapter(recMoviesAdapter);

        if (recMovies.isEmpty()) {
            View recMoviesTitleView = findViewById(R.id.recMovieTitle);
            recMoviesTitleView.setVisibility(View.GONE);
        }

        recyclerView.addOnItemTouchListener(
                new RecyclerItemClickListener(getApplicationContext(), recyclerView ,new RecyclerItemClickListener.OnItemClickListener() {
                    @Override public void onItemClick(View view, int position) {
                        searchMovie.clickedFromSearch=true;
                        searchMovie.jsonMoviesSearchMovie=recMovies;
                        soonpage.clickedMovie=recMovies.get(position).getId();
                        Intent myintent=new Intent(getApplicationContext(),movieDetails.class);
                        startActivity(myintent);
                        finish();
                    }

                    @Override public void onLongItemClick(View view, int position) {

                    }
                })
        );

        movieObj item;

        if(mostanticipated.clickedFromMA){
            item = movieObj.getMovieByID(mostanticipated.jsonMoviesMA, soonpage.clickedMovie);
            mostanticipated.clickedFromMA=false;
        }
        else if(selectedGenreMovies.clickedFromSGM){
            item = movieObj.getMovieByID(selectedGenreMovies.jsonMovies,soonpage.clickedMovie);
            selectedGenreMovies.clickedFromSGM=false;
        }
        else if(favorites.clickedFromFavs){
            item = movieObj.getMovieByID(favorites.jsonMoviesFavs,soonpage.clickedMovie);
            favorites.clickedFromFavs=false;
        }
        else if(watchlist.clickedFromWatchlist){
            item = movieObj.getMovieByID(watchlist.jsonMoviesWatchlist,soonpage.clickedMovie);
            watchlist.clickedFromWatchlist=false;
        }
        else if(reminder.clickedFromReminder){
            item = movieObj.getMovieByID(reminder.jsonMoviesReminder,soonpage.clickedMovie);
            reminder.clickedFromReminder=false;
        }
        else if(searchMovie.clickedFromSearch){
            item = movieObj.getMovieByID(searchMovie.jsonMoviesSearchMovie,soonpage.clickedMovie);
            searchMovie.clickedFromSearch=false;
        }
        else{
            item = movieObj.getMovieByID(soonpage.jsonMovies,soonpage.clickedMovie);
        }

        movieName.setText(item.getTitle());
        setTitle(item.getTitle().toUpperCase());

        String imgPath= "https://image.tmdb.org/t/p/w780"+item.getBackdrop_path();
        //String imgPath= "https://image.tmdb.org/t/p/w300"+item.getBackdrop_path();

        Glide.with(getApplicationContext())
                .load(imgPath)
                .into(moviePoster);


        if(item.getVote_average()>0){
            movieRate.setText(item.getVote_average()+"/10");
        }
        else{
            movieRate.setText("-/10");
        }
        addFavorites.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
             @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                 if(isChecked){
                     String id=firebaseAuth.getCurrentUser().getUid();

                     int movie_id=soonpage.clickedMovie;
                     List<Integer> movie_ids=new ArrayList<>();

                     for(movieToSave movies:favoriteMovies){
                         if(movies.getUser_id().equals(id)){
                             movie_ids=movies.getMovie_id();
                             if(!movieToSave.hasContain(movie_ids,movie_id)){
                                 movie_ids.add(movie_id);
                             }
                         }
                     }

                     movieToSave movie=new movieToSave(id,movie_ids);
                     dbFavorites.child(id).setValue(movie);

                     if(!alreadyinFavs){
                        /* MediaPlayer mpFa = MediaPlayer.create(getApplicationContext(),R.raw.fillingyourinbox);
                         mpFa.start();*/
                         Toast.makeText(getApplicationContext(), "Movie has been added to your favorites.",Toast.LENGTH_SHORT).show();
                         //mpFa.seekTo(0);
                     }
                 }else{
                     String id=firebaseAuth.getCurrentUser().getUid();

                     int movie_id=soonpage.clickedMovie;
                     List<Integer> movie_ids=new ArrayList<>();

                     for(movieToSave movies:favoriteMovies){
                         if(movies.getUser_id().equals(id)){
                             movie_ids=movies.getMovie_id();
                             movie_ids=movieToSave.removeItem(movie_ids,movie_id);
                         }
                     }

                     movieToSave movie=new movieToSave(id,movie_ids);
                     dbFavorites.child(id).setValue(movie);

                     /*MediaPlayer mpFr = MediaPlayer.create(getApplicationContext(),R.raw.caseclosed);
                     mpFr.start();*/
                     Toast.makeText(getApplicationContext(), "Movie has been removed from your favorites.",Toast.LENGTH_SHORT).show();
                     //mpFr.seekTo(0);
                 }
            }
        }
        );

        addWatchlist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked){
                    String id=firebaseAuth.getCurrentUser().getUid();

                    int movie_id=soonpage.clickedMovie;
                    List<Integer> movie_ids=new ArrayList<>();

                    for(movieToSave movies:watchlistMovies){
                        if(movies.getUser_id().equals(id)){
                            movie_ids=movies.getMovie_id();
                            if(!movieToSave.hasContain(movie_ids,movie_id)){
                                movie_ids.add(movie_id);
                            }
                        }
                    }

                    movieToSave movie=new movieToSave(id,movie_ids);
                    dbWatchlist.child(id).setValue(movie);

                    if(!alreadyinWatch){
                        Toast.makeText(getApplicationContext(), "Movie has been added to your watchlist.",Toast.LENGTH_SHORT).show();
                    }
                }else{
                    String id=firebaseAuth.getCurrentUser().getUid();

                    int movie_id=soonpage.clickedMovie;
                    List<Integer> movie_ids=new ArrayList<>();

                    for(movieToSave movies:watchlistMovies){
                        if(movies.getUser_id().equals(id)){
                            movie_ids=movies.getMovie_id();
                            movie_ids=movieToSave.removeItem(movie_ids,movie_id);
                        }
                    }

                    movieToSave movie=new movieToSave(id,movie_ids);
                    dbWatchlist.child(id).setValue(movie);

                    Toast.makeText(getApplicationContext(), "Movie has been removed from your watchlist.",Toast.LENGTH_SHORT).show();
                }
            }
        }
        );

        setReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if(isChecked){
                    String id=firebaseAuth.getCurrentUser().getUid();

                    int movie_id=soonpage.clickedMovie;
                    List<Integer> movie_ids=new ArrayList<>();

                    for(movieToSave movies:reminderMovies){
                        if(movies.getUser_id().equals(id)){
                            movie_ids=movies.getMovie_id();
                            if(!movieToSave.hasContain(movie_ids,movie_id)){
                                movie_ids.add(movie_id);
                            }
                        }
                    }

                    movieToSave movie=new movieToSave(id,movie_ids);
                    dbReminder.child(id).setValue(movie);

                    if(!alreadyinReminder){
                        Date today=new Date();
                        Calendar cal=Calendar.getInstance();
                        cal.setTime(today);
                        String[] releaseDateArray=movieReleaseDate.getText().toString().split("-");
                        int year=Integer.parseInt(releaseDateArray[0]);
                        int month=Integer.parseInt(releaseDateArray[1]);
                        int day=Integer.parseInt(releaseDateArray[2]);

                        int dayLeft=((year-cal.get(Calendar.YEAR))*365)+((month-cal.get(Calendar.MONTH)-1)*31)+((day-cal.get(Calendar.DAY_OF_MONTH)));
                        try{
                            setAlarmForMovie(movieName.getText().toString(),releaseDateArray);
                        }catch (ParseException e){
                            System.out.println("PARSE EXCEPTION ERROR");
                            e.fillInStackTrace();
                        }
                        Toast.makeText(getApplicationContext(), "You set reminder for this movie.",Toast.LENGTH_SHORT).show();
                    }
                }else{
                    String id=firebaseAuth.getCurrentUser().getUid();

                    int movie_id=soonpage.clickedMovie;
                    List<Integer> movie_ids=new ArrayList<>();

                    for(movieToSave movies:reminderMovies){
                        if(movies.getUser_id().equals(id)){
                            movie_ids=movies.getMovie_id();
                            movie_ids=movieToSave.removeItem(movie_ids,movie_id);
                        }
                    }

                    movieToSave movie=new movieToSave(id,movie_ids);
                    dbReminder.child(id).setValue(movie);
                    try{
                        cancelAlarmForMovie(movieName.getText().toString(),movieReleaseDate.getText().toString());
                    }catch (ParseException e){
                        System.out.println("PARSE EXCEPTION ERROR");
                        e.fillInStackTrace();
                    }
                    Toast.makeText(getApplicationContext(), "You unset the reminder of this movie.",Toast.LENGTH_SHORT).show();
                }

                /*if(publication.getStatus().equals("Released")){
                    setReminder.setChecked(false);
                }*/
            }
        }
        );

        dbFavorites.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                favoriteMovies.clear();
                for(DataSnapshot usersSnapShot : dataSnapshot.getChildren()){
                    if(usersSnapShot.getValue(movieToSave.class).getUser_id().equals(firebaseAuth.getCurrentUser().getUid())){
                        favoriteMovies.add(usersSnapShot.getValue(movieToSave.class));
                        if(movieToSave.hasContain(usersSnapShot.getValue(movieToSave.class).getMovie_id(),soonpage.clickedMovie)){
                            alreadyinFavs =true;
                            addFavorites.setChecked(true);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        dbWatchlist.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                watchlistMovies.clear();
                for(DataSnapshot usersSnapShot : dataSnapshot.getChildren()){
                    if(usersSnapShot.getValue(movieToSave.class).getUser_id().equals(firebaseAuth.getCurrentUser().getUid())){
                        watchlistMovies.add(usersSnapShot.getValue(movieToSave.class));
                        if(movieToSave.hasContain(usersSnapShot.getValue(movieToSave.class).getMovie_id(),soonpage.clickedMovie)){
                            alreadyinWatch =true;
                            addWatchlist.setChecked(true);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        dbReminder.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                reminderMovies.clear();
                for(DataSnapshot usersSnapShot : dataSnapshot.getChildren()){
                    if(usersSnapShot.getValue(movieToSave.class).getUser_id().equals(firebaseAuth.getCurrentUser().getUid())){
                        reminderMovies.add(usersSnapShot.getValue(movieToSave.class));
                        if(movieToSave.hasContain(usersSnapShot.getValue(movieToSave.class).getMovie_id(),soonpage.clickedMovie)){
                            alreadyinReminder =true;
                            setReminder.setChecked(true);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    private void watchYoutubeVideo(String id) {
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id));
        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://www.youtube.com/watch?v=" + id));
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException ex) {
            startActivity(webIntent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share, menu);
        return true;
    }

    private void setAlarmForMovie(String movieName,String[] releasingDate) throws ParseException {
        String dt = releasingDate[0]+"-"+releasingDate[1]+"-"+releasingDate[2]+" "+String.valueOf(11)+":"+String.valueOf(00)+":"+String.valueOf(00);  // Start date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar c = Calendar.getInstance();
        c.setTime(sdf.parse(dt));
        c.add(Calendar.DATE,-2);  // number of days to add
        int[] alarmDate=new int[3];
        alarmDate[0]=c.get(Calendar.YEAR);
        alarmDate[1]=c.get(Calendar.MONTH)-1;
        alarmDate[2]=c.get(Calendar.DAY_OF_MONTH);

        //c.set(alarmDate[0], alarmDate[1], alarmDate[2], 12, 00, 0);

        dt = sdf.format(c.getTime());  // dt is now the new date
        System.out.println(dt+" is the date and time for reminder.");

        alarmIntent = new Intent(movieDetails.this, AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(movieDetails.this, publication.getId(), alarmIntent, 0);
        //pendingIntent = PendingIntent.getBroadcast(movieDetails.this, soonpage.clickedMovie, alarmIntent, 0);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        alarmManager.set(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),pendingIntent);

    }

    private void cancelAlarmForMovie(String movieName,String releasingDate) throws ParseException {
        String dt = releasingDate;  // Start date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(sdf.parse(dt));
        c.add(Calendar.DATE,1);  // number of days to add
        dt = sdf.format(c.getTime());  // dt is now the new date

        alarmIntent = new Intent(movieDetails.this, AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(movieDetails.this, publication.getId(), alarmIntent, 0);
        //pendingIntent = PendingIntent.getBroadcast(movieDetails.this, soonpage.clickedMovie, alarmIntent, 0);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        alarmManager.cancel(pendingIntent);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle arrow click here
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        switch (item.getItemId()) {
            case R.id.menu_item_share:
                Intent myIntent=new Intent(Intent.ACTION_SEND);
                myIntent.setType("text/plain");
                String shareTitle=publication.getTitle()+"\n"+"https://www.themoviedb.org/movie/"+publication.getId()+"\n\n"+"Shared with Cinemind";
                //String shareTitle=movieName.getText()+"\n"+"https://www.themoviedb.org/movie/"+soonpage.clickedMovie+"\n\n"+"Shared with Cinemind";
                //String shareDesc="Cinemind";
                //myIntent.putExtra(Intent.EXTRA_SUBJECT,shareDesc);
                myIntent.putExtra(Intent.EXTRA_TEXT,shareTitle);
                startActivity(Intent.createChooser(myIntent,"Share the Movie"));
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendRequest(){
        gotDetails=true;
        movieDetailsRequestOperator.urlToRequest="https://api.themoviedb.org/3/movie/"+soonpage.clickedMovie+"?api_key=a092bd16da64915723b2521295da3254&append_to_response=credits,videos,images";
        movieDetailsRequestOperator ro= new movieDetailsRequestOperator();
        ro.setListener(this);
        ro.start();
    }

    private void recommendedMoviesRequest(){
        gotRecommendedMovies=true;
        movieRequestOperator.urlToRequest="https://api.themoviedb.org/3/movie/"+soonpage.clickedMovie+"/recommendations?api_key=a092bd16da64915723b2521295da3254&language=en-US&page=1";
        movieRequestOperator ro= new movieRequestOperator();
        ro.setListener(this);
        ro.start();
    }

    public void updatePublication(){
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                if(publication!=null && gotDetails && !gotRecommendedMovies){
                    movieReleaseDate.setText(publication.getRelease_date());

                    String[] releaseDateArray = publication.getRelease_date().split("-");

                    int year = Integer.parseInt(releaseDateArray[0]);
                    int month = Integer.parseInt(releaseDateArray[1]);
                    int day = Integer.parseInt(releaseDateArray[2]);

                    Date today = new Date();
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(today);

                    Calendar cal2 = Calendar.getInstance();
                    cal2.set(year, month - 1, day, 0, 0);

                    long msDiff =cal2.getTimeInMillis()-  cal.getInstance().getTimeInMillis();
                    long daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff);

                    if(daysDiff<=0){
                        //this movie is already released you cant set reminder for it
                        setReminder.setVisibility(View.INVISIBLE);
                        setReminder.setClickable(false);
                    }
                    if(publication.getLength()>0){
                        movieLength.setText(publication.getLength()+" min");
                    }
                    else{
                        movieLength.setText("- min");
                    }
                    String genresOfMovie = new String();
                    for(int i=0;i<publication.getGenres().size();i++){
                        if(publication.getGenres().size()-1 == i){
                            genresOfMovie+=publication.getGenres().get(i).getTitle().toString();
                        }
                        else{
                            genresOfMovie+=publication.getGenres().get(i).getTitle().toString()+" • ";
                        }
                    }

                    movieOverview.setText(publication.getOverview());

                    movieGenres.setText(genresOfMovie);

                    String director="";

                    for(int i=0;i<publication.getCrewList().size();i++){
                        if(publication.getCrewList().get(i).getJob().equals("Director") && director==""){
                            director=publication.getCrewList().get(i).getName();
                        }
                        else if(publication.getCrewList().get(i).getJob().equals("Director") && director!=""){
                            director+=", "+publication.getCrewList().get(i).getName();
                        }
                    }

                    movieDirector.setText("By "+director);

                    for(int i=0;i<publication.getCastList().size();i++){
                        castList.add(new cast(publication.getCastList().get(i).getId(), publication.getCastList().get(i).getName(), publication.getCastList().get(i).getImagePath(), publication.getCastList().get(i).getCharacter()));
                    }
                    castLv.invalidateViews();


                    for(int i=0;i<publication.getCrewList().size();i++){
                        crewList.add(new crew(publication.getCrewList().get(i).getId(), publication.getCrewList().get(i).getName(), publication.getCrewList().get(i).getImagePath(), publication.getCrewList().get(i).getDepartment(),publication.getCrewList().get(i).getJob()));
                    }
                    crewLv.invalidateViews();

                    if(publication.getVideoList().isEmpty() || publication.getVideoList()==null){
                        watchTrailer.setVisibility(View.INVISIBLE);
                    }

                    final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
                    int pixels = (int) (240 * scale + 0.5f);

                    if(publication.getCrewList()!=null && !publication.getCrewList().isEmpty()){
                        ViewGroup.LayoutParams params = crewLv.getLayoutParams();
                        params.height = pixels;
                        crewLv.setLayoutParams(params);
                    }
                    if(publication.getCastList()!=null && !publication.getCastList().isEmpty()){
                        ViewGroup.LayoutParams params = castLv.getLayoutParams();
                        params.height = pixels;
                        castLv.setLayoutParams(params);
                    }

                    fillViewPager();
                    recommendedMoviesRequest();

                }
                else{

                }

                if(publications!=null && !publications.isEmpty() && gotDetails && gotRecommendedMovies){
                    for(int i=0;i<publications.size();i++){
                        recMovies.add(new movieObj(publications.get(i).getId(),publications.get(i).getTitle(),publications.get(i).getRelease_date(),publications.get(i).getPoster_path()));
                        recMovies.get(i).setBackdrop_path(publications.get(i).getBackdrop_path());
                        //System.out.println(publications.get(i).getTitle()+" is recommended. from movie details.");
                    }
                    final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
                    int pixels = (int) (350 * scale + 0.5f);

                    ViewGroup.LayoutParams params = viewPager.getLayoutParams();
                    params.height = pixels;
                    viewPager.setLayoutParams(params);
                    recMoviesAdapter.notifyDataSetChanged();

                    if (!recMovies.isEmpty()) {
                        View recMoviesTitleView = findViewById(R.id.recMovieTitle);
                        recMoviesTitleView.setVisibility(View.VISIBLE);
                    }
                }
                else{
                }
            }
        });

    }

    public void fillViewPager(){
        ViewPagerAdapter.images=publication.getImagesList();
        viewPager = (ViewPager) findViewById(R.id.myViewPager);
        viewPager.setPageMargin(0);
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);
        if (publication.getImagesList().isEmpty()) {
            View imagesView = findViewById(R.id.movieImagesLinear);
            imagesView.setVisibility(View.GONE);
        }
    }


    @Override
    public void success(movieObj publication) {
        this.publication=publication;
        updatePublication();
    }

    @Override
    public void success(List<movieObj> publications) {
        this.publications=publications;
        updatePublication();
    }

    @Override
    public void failed(int responseCode){
        this.publications=null;
        //this.publication=null;
        updatePublication();
    }

    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {

        if(!b){
            youTubePlayer.cueVideo(publication.getVideoList().get(0).getKey());
        }

    }

    @Override
    public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

    }
}
