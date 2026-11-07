package com.englishup.app;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;
import android.provider.Settings;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
  private static final int MIC=11,VOICE=12;
  private static final int NAVY=Color.rgb(17,37,85), RED=Color.rgb(237,36,65), WHITE=Color.WHITE, MUTED=Color.rgb(91,108,143), BG=Color.rgb(15,35,90);
  private int xp(){return getPreferences(0).getInt("xp",0);}
  private void addXp(int n){getPreferences(0).edit().putInt("xp",xp()+n).apply();}

  private final ArrayList<Lesson> lessons=new ArrayList<>();
  private final ArrayList<Lesson> active=new ArrayList<>();
  private final ArrayList<String[]> vocab=new ArrayList<>(), interpretations=new ArrayList<>();
  private final ArrayList<String> speech=new ArrayList<>();
  private final Random random=new Random();
  private TextToSpeech tts; private TextView feedback;
  private String mode="home",module="",expectedSpeech="",track="Essencial";
  private boolean placement=false,ready=false; private int question=0,lessonIndex=0,quizScore=0,quizIndex=0;
  private int[] results={0,0,0};
  private String normalizePt(String raw){
    return java.text.Normalizer.normalize(raw.toLowerCase(new Locale("pt","BR")),java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
  }
  private String[] acceptedMeanings(String english,String expected){
    String e=english.toLowerCase(Locale.ROOT).trim();
    if(e.equals("good night"))return new String[]{"boa noite", "boa noite ao sair", "boa noite ao se despedir", "boa noite na despedida", "boa noite antes de dormir", "desejar boa noite"};
    if(e.equals("good morning"))return new String[]{"bom dia", "desejar bom dia"};
    if(e.equals("good afternoon"))return new String[]{"boa tarde", "desejar boa tarde"};
    if(e.equals("good evening"))return new String[]{"boa noite", "cumprimento a noite", "boa noite ao chegar"};
    if(e.equals("thank you")||e.equals("thanks"))return new String[]{"obrigado","obrigada","agradecer","muito obrigado","muito obrigada"};
    if(e.equals("you're welcome")||e.equals("you are welcome"))return new String[]{"de nada","por nada","nao ha de que","disponha"};
    if(e.equals("excuse me"))return new String[]{"com licenca","desculpe","desculpa","com licenca por favor"};
    if(e.equals("sorry"))return new String[]{"desculpe","desculpa","sinto muito","perdao","me desculpe"};
    if(e.equals("see you later"))return new String[]{"ate mais","ate logo","te vejo mais tarde","nos vemos mais tarde"};
    if(e.equals("please"))return new String[]{"por favor","por gentileza"};
    return new String[]{expected};
  }
  private int meaningMatch(String english,String expected,String answer){
    String got=normalizePt(answer);if(got.isEmpty())return 0;
    for(String variant:acceptedMeanings(english,expected)){
      String n=normalizePt(variant);
      if(got.equals(n))return 2;
    }
    // Some entries include clarifications in parentheses or alternative definitions.
    String clean=expected.replaceAll("\\([^)]*\\)","");
    for(String part:clean.split("[/;,]"))if(got.equals(normalizePt(part)))return 2;
    // Don't auto-fail possible synonyms; let the student confirm after explanation.
    return 1;
  }
  private static class DialogTurn {String prompt,example;DialogTurn(String p,String e){prompt=p;example=e;}}
  private final ArrayList<DialogTurn> dialog=new ArrayList<>();
  private int dialogIndex=0;
  private void conversationHome(){mode="conversation";module="Conversação prática";
    LinearLayout box=page("💬 Conversação prática","Pratique inglês com sua tutora virtual.");
    featurePhoto(box,R.drawable.tutora_hero);
    tutorCard(box,"Hi! I'm your English tutor!","Escolha uma situação para conversar comigo. Eu faço perguntas em inglês e mostro exemplos. Ainda sou um bot local, não uma IA generativa.");
    button(box,"✈️ Viagens",v->startConversation("Viagens"));
    button(box,"💼 Trabalho",v->startConversation("Trabalho"));
    button(box,"🎓 Meio acadêmico",v->startConversation("Acadêmico"));
    button(box,"🏡 Vida cotidiana",v->startConversation("Cotidiano"));
    secondary(box,"🏠 Voltar ao menu",v->home());
  }
  private void startConversation(String topic){track=topic;dialog.clear();dialogIndex=0;
    if(topic.equals("Viagens")){
      dialog.add(new DialogTurn("Hello! Where are you traveling today?","I'm traveling to New York."));
      dialog.add(new DialogTurn("Do you have any luggage to check in?","Yes, I have one suitcase."));
      dialog.add(new DialogTurn("What time is your flight?","My flight is at 8 PM."));
      dialog.add(new DialogTurn("Would you prefer a window or an aisle seat?","I'd prefer a window seat, please."));
    }else if(topic.equals("Trabalho")){
      dialog.add(new DialogTurn("Good morning! Can you introduce yourself?","My name is Alex. I'm happy to meet you."));
      dialog.add(new DialogTurn("What kind of work do you do?","I work in customer service."));
      dialog.add(new DialogTurn("Can you help me with this task?","Of course. What do you need?"));
      dialog.add(new DialogTurn("When can you finish the project?","I can finish it by Friday."));
    }else if(topic.equals("Acadêmico")){
      dialog.add(new DialogTurn("Hi! What are you studying?","I'm studying business administration."));
      dialog.add(new DialogTurn("Do you have a class this afternoon?","Yes, I have English class at two."));
      dialog.add(new DialogTurn("Could you explain your project?","My project is about renewable energy."));
      dialog.add(new DialogTurn("When is your assignment due?","It's due next Monday."));
    }else{
      dialog.add(new DialogTurn("Hi! How are you doing today?","I'm doing well, thanks!"));
      dialog.add(new DialogTurn("What do you usually do on weekends?","I usually go to the gym."));
      dialog.add(new DialogTurn("What would you like to eat?","I'd like a sandwich, please."));
      dialog.add(new DialogTurn("Do you have any plans for tomorrow?","I'm going to visit my family."));
    }
    Collections.shuffle(dialog,random);conversationTurn();
  }
  private void conversationTurn(){mode="conversation";
    if(dialogIndex>=dialog.size()){
      LinearLayout box=page("🎉 Conversa concluída","Você praticou quatro situações de "+track+".");
      button(box,"🔁 Outra rodada",v->startConversation(track));secondary(box,"🏠 Menu",v->home());return;
    }
    DialogTurn turn=dialog.get(dialogIndex);
    LinearLayout box=page("💬 Conversação • "+track,"Pergunta "+(dialogIndex+1)+" de "+dialog.size());
    tutorCard(box,"EnglishUp • Tutora virtual",turn.prompt);
    button(box,"🔊 Ouvir pergunta",v->speak(turn.prompt));
    text(box,"Responda em inglês, com suas próprias palavras:",16,false);
    EditText response=new EditText(this);response.setSingleLine(false);response.setMinLines(2);response.setHint("Type your answer in English...");box.addView(response);
    button(box,"💬 Enviar resposta",v->{String answer=response.getText().toString().trim();if(answer.isEmpty()){Toast.makeText(this,"Escreva sua resposta em inglês.",Toast.LENGTH_SHORT).show();return;}
      String normalized=answer.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]"," ").trim();
      String note=normalized.split("\\s+").length<3?"Sua resposta é curta. Tente formar uma frase completa.":"Boa tentativa! Confira se sua resposta realmente atende à pergunta e observe o exemplo.";
      new android.app.AlertDialog.Builder(this).setTitle("💬 Feedback da tutora")
        .setMessage("Você escreveu: "+answer+"\n\n"+note+"\n\nExemplo possível: "+turn.example+"\n\nEste bot ainda não confirma automaticamente se sua resposta está correta.")
        .setPositiveButton("Próxima pergunta",(d,w)->{dialogIndex++;conversationTurn();})
        .setNegativeButton("Tentar novamente",(d,w)->{})
        .show();
    });
    secondary(box,"🏠 Menu",v->home());
  }
  private boolean reduceMotion(){return getPreferences(0).getBoolean("reduce_motion",false);}
  private void animateIn(View view){if(reduceMotion())return;view.setAlpha(0f);view.setTranslationX(dp(26));view.animate().alpha(1f).translationX(0f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();}
  private void transition(Runnable next){View root=getWindow().getDecorView().findViewById(android.R.id.content);if(reduceMotion()||root==null){next.run();return;}root.animate().alpha(0.35f).translationX(-dp(18)).setDuration(125).withEndAction(()->{root.setAlpha(1f);root.setTranslationX(0);next.run();}).start();}
  private void navigate(String m){transition(()->choose(m));}
  private String level(String key){int score=getPreferences(0).getInt(key,0);return score<=3?"A1":score<=6?"A2":score<=8?"B1":"B2";}
  private void levelCard(LinearLayout box,String name,String key,String description){int score=getPreferences(0).getInt(key,0);card(box,name+" • "+level(key),description);progressBar(box,score,10);}
  private void rememberMistake(String answer){
    String key="review_"+module+"_"+track;
    String old=getPreferences(0).getString(key,"");
    if(!old.contains("|"+answer+"|"))getPreferences(0).edit().putString(key,old+"|"+answer+"|").apply();
  }
  private void review(){LinearLayout box=page("🔁 Sua revisão","Itens errados em cada trilha para praticar novamente.");
    int found=0;
    for(String m:new String[]{"Vocabulário","Conversação","Verbos","Interpretação"})
      for(String t:new String[]{"Essencial","Viagens","Trabalho","Acadêmico"}){
        String old=getPreferences(0).getString("review_"+m+"_"+t,"");
        if(!old.isEmpty()){found++;card(box,m+" • "+t,old.replace("|","  •  "));}
      }
    if(found==0)card(box,"Tudo em dia!","Suas respostas incorretas aparecerão aqui.");
    secondary(box,"🏠 Menu",v->home());
  }
  private final ArrayList<Reading> readings=new ArrayList<>();
  private static class Reading {String title,passage,track="Essencial";ArrayList<String[]> qs=new ArrayList<>();}
  private int readingIndex=0;
  private static class Lesson {String category,en,pt,tip,level,track;Lesson(JSONObject j){category=j.optString("category");en=j.optString("en");pt=j.optString("pt");tip=j.optString("tip");level=j.optString("level");track=j.optString("track","Essencial");}}
  @Override public void onCreate(Bundle b){super.onCreate(b);load();tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)ready=tts.setLanguage(Locale.US)>=TextToSpeech.LANG_AVAILABLE;});try{JSONArray rr=new JSONArray(asset("reading.json"));for(int j=0;j<rr.length();j++){JSONObject o=rr.getJSONObject(j);Reading r=new Reading();r.title=o.getString("title");r.passage=o.getString("passage");r.track=o.optString("track","Essencial");JSONArray qs=o.getJSONArray("questions");for(int k=0;k<qs.length();k++){JSONArray row=qs.getJSONArray(k);r.qs.add(new String[]{row.getString(0),row.getString(1),row.getString(2),row.getString(3),row.getString(4)});}readings.add(r);}}catch(Exception ignored){}if(!getPreferences(0).getBoolean("placed",false))welcome();else home();}
  private String asset(String name)throws Exception{InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();return new String(out.toByteArray(),StandardCharsets.UTF_8);}
  private void load(){try{JSONArray a=new JSONArray(asset("lessons.json"));for(int i=0;i<a.length();i++)lessons.add(new Lesson(a.getJSONObject(i)));JSONObject p=new JSONObject(asset("placement.json"));JSONArray v=p.getJSONArray("vocab"),t=p.getJSONArray("interpret"),s=p.getJSONArray("speech");for(int i=0;i<v.length();i++){JSONArray row=v.getJSONArray(i);vocab.add(new String[]{row.getString(0),row.getString(1)});}for(int i=0;i<t.length();i++){JSONArray row=t.getJSONArray(i);interpretations.add(new String[]{row.getString(0),row.getString(1)});}for(int i=0;i<s.length();i++)speech.add(s.getString(i));}catch(Exception e){Toast.makeText(this,"Erro ao carregar conteúdo: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private GradientDrawable glowRed(){
    GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      new int[]{Color.rgb(255,86,108),RED,Color.rgb(190,17,49)});
    d.setCornerRadius(dp(18)); d.setStroke(dp(1),Color.rgb(255,126,142));return d;
  }
  private void mistake(String correct,String explanation,Runnable next){
    android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).create();
    LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(18),dp(22),dp(20));content.setBackground(shape(Color.rgb(240,245,255),22));
    text(content,"✨  Feedback da atividade",23,true);
    text(content,"Cada tentativa é uma oportunidade de melhorar.",14,false);
    LinearLayout answer=new LinearLayout(this);answer.setOrientation(LinearLayout.VERTICAL);answer.setPadding(dp(14),dp(10),dp(14),dp(10));answer.setBackground(outlined(Color.rgb(228,237,255),Color.rgb(193,210,253),16));content.addView(answer);
    text(answer,"🎯  RESPOSTA DE REFERÊNCIA",13,true);text(answer,correct,19,true);
    text(content,"🔎  Entenda o significado",17,true);text(content,explanation,15,false);
    text(content,"Dica: observe o contexto e o significado, não apenas palavras isoladas.",14,false);
    button(content,"Entendi • Continuar  →",v->{dialog.dismiss();next.run();});dialog.setView(content);dialog.setCancelable(false);dialog.show();
  }
  private GradientDrawable outlined(int color,int stroke,int radius){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),stroke);return d;}
  private LinearLayout page(String title,String subtitle){
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(12,29,76),Color.rgb(33,67,162),Color.rgb(34,77,214)}));
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(16),dp(16),dp(30));box.setBackground(shape(Color.rgb(246,249,255),26));
    ScrollView.LayoutParams contentParams=new ScrollView.LayoutParams(-1,-2);
    contentParams.setMargins(dp(12),dp(16),dp(12),dp(16));scroll.addView(box,contentParams);setContentView(scroll);animateIn(box);
    LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.HORIZONTAL);brand.setGravity(android.view.Gravity.CENTER_VERTICAL);brand.setPadding(dp(14),dp(17),dp(14),dp(17));brand.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(14,34,91),Color.rgb(37,72,173),Color.rgb(23,45,109)}));
    TextView flag=new TextView(this);flag.setText("🇺🇸 ✦");flag.setTextSize(24);brand.addView(flag);
    LinearLayout brandText=new LinearLayout(this);brandText.setOrientation(LinearLayout.VERTICAL);brandText.setPadding(dp(10),0,0,0);brand.addView(brandText,new LinearLayout.LayoutParams(0,-2,1));
    TextView name=new TextView(this);name.setText("EnglishUp  🇺🇸");name.setTextSize(21);name.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));name.setTextColor(WHITE);brandText.addView(name);
    TextView sub=new TextView(this);sub.setText("LEARN  •  SPEAK  •  LEVEL UP");sub.setTextSize(10);sub.setTextColor(Color.rgb(221,228,240));brandText.addView(sub);
    box.addView(brand);TextView titleView=text(box,title,27,true);titleView.setPadding(dp(3),dp(22),dp(3),dp(4));if(!subtitle.isEmpty()){TextView st=text(box,subtitle,15,false);st.setTextColor(MUTED);}
    return box;
  }
  private TextView text(LinearLayout box,String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(NAVY);t.setTypeface(Typeface.create("sans-serif-rounded",bold?Typeface.BOLD:Typeface.NORMAL));t.setPadding(dp(4),dp(9),dp(4),dp(9));box.addView(t);return t;}
  private void button(LinearLayout box,String label,View.OnClickListener cb){
    Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));b.setTextColor(WHITE);
    b.setBackground(glowRed());b.setElevation(dp(7));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(6),0,dp(6));box.addView(b,lp);b.setOnClickListener(v->{if(!reduceMotion()){b.animate().scaleX(.97f).scaleY(.97f).setDuration(80).withEndAction(()->{b.setScaleX(1);b.setScaleY(1);cb.onClick(v);}).start();}else cb.onClick(v);});
  }
  private void secondary(LinearLayout box,String label,View.OnClickListener cb){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(NAVY);b.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));
    b.setBackground(outlined(WHITE,Color.rgb(207,218,248),15));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.setMargins(0,dp(5),0,dp(5));box.addView(b,lp);b.setOnClickListener(cb);}
  private void tutorCard(LinearLayout box,String title,String message){
    LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.HORIZONTAL);tile.setGravity(android.view.Gravity.CENTER_VERTICAL);tile.setPadding(dp(12),dp(12),dp(12),dp(12));tile.setBackground(outlined(WHITE,Color.rgb(210,221,252),20));tile.setElevation(dp(4));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(12),0,dp(12));box.addView(tile,lp);
    TextView avatar=new TextView(this);avatar.setText("👩🏻‍🏫");avatar.setTextSize(42);avatar.setGravity(android.view.Gravity.CENTER);avatar.setBackground(shape(Color.rgb(232,239,255),18));tile.addView(avatar,new LinearLayout.LayoutParams(dp(84),dp(84)));
    LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(8),0,0,0);tile.addView(content,new LinearLayout.LayoutParams(0,-2,1));text(content,title,17,true);TextView body=text(content,message,15,false);body.setTextColor(MUTED);
  }
  private void card(LinearLayout box,String title,String detail){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(12),dp(15),dp(12));c.setBackground(outlined(WHITE,Color.rgb(231,235,242),17));c.setElevation(dp(1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));box.addView(c,lp);text(c,title,18,true);TextView d=text(c,detail,14,false);d.setTextColor(MUTED);}
  private void progressBar(LinearLayout box,int done,int total){ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.setMax(Math.max(1,total));p.setProgress(done);p.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));p.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(229,233,241)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(8));lp.setMargins(0,dp(12),0,dp(14));box.addView(p,lp);}
  private void picture(LinearLayout box,String word){int id=getResources().getIdentifier("pic_"+word.toLowerCase(Locale.US),"drawable",getPackageName());if(id==0)return;LinearLayout c=new LinearLayout(this);c.setGravity(android.view.Gravity.CENTER);c.setBackground(outlined(WHITE,Color.rgb(230,234,240),18));LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(dp(158),dp(158));outer.gravity=1;outer.setMargins(0,dp(14),0,dp(14));box.addView(c,outer);ImageView img=new ImageView(this);img.setImageResource(id);img.setScaleType(ImageView.ScaleType.FIT_CENTER);c.addView(img,new LinearLayout.LayoutParams(dp(130),dp(130)));animateIn(c);}
  private void speak(String s){if(!ready){Toast.makeText(this,"Voz americana indisponível nas configurações do Android",Toast.LENGTH_LONG).show();return;}tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"englishup");}
  private void listen(String target){expectedSpeech=target;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Fale a frase em inglês");startActivityForResult(i,VOICE);}catch(Exception e){if(feedback!=null)feedback.setText("Reconhecimento de voz indisponível. Verifique os serviços de fala do Android.");}}
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grant){super.onRequestPermissionsResult(request,permissions,grant);if(request==MIC&&grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED)listen(expectedSpeech);else if(request==MIC&&feedback!=null)feedback.setText("Permissão do microfone necessária para praticar.");}
  private String norm(String s){return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]","").replaceAll("\\s+"," ").trim();}
  private double match(String said,String expected){String[] a=norm(expected).split(" "),b=norm(said).split(" ");int[][] d=new int[a.length+1][b.length+1];for(int i=0;i<=a.length;i++)d[i][0]=i;for(int j=0;j<=b.length;j++)d[0][j]=j;for(int i=1;i<=a.length;i++)for(int j=1;j<=b.length;j++)d[i][j]=Math.min(d[i-1][j-1]+(a[i-1].equals(b[j-1])?0:1),Math.min(d[i-1][j]+1,d[i][j-1]+1));return Math.max(0,1.0-(double)d[a.length][b.length]/Math.max(a.length,b.length));}
  @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=VOICE)return;if(result!=RESULT_OK||data==null){if(feedback!=null)feedback.setText("Não foi possível reconhecer. Tente novamente.");return;}ArrayList<String> list=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(list==null||list.isEmpty())return;String heard=list.get(0);int score=(int)Math.round(100*match(heard,expectedSpeech));if(placement){if(score>=75)results[2]++;question++;placementQuestion();}else if(feedback!=null){feedback.setText("Você disse: "+heard+"\nCorrespondência das palavras: "+score+"%\nIsso NÃO é nota fonética. Ouça e repita para melhorar.");}}
  private void welcome(){mode="welcome";LinearLayout box=page("🇺🇸 EnglishUp Ultimate","Seu inglês começa com um desafio!");text(box,"🎯 Teste inicial de 30 questões",23,true);text(box,"10 de vocabulário • 10 de interpretação • 10 de frases faladas. O resultado recomenda seu ponto de partida, sem prometer certificação oficial.",17,false);text(box,"Na parte de fala usamos reconhecimento do Android. A avaliação avançada de sons e entonação exigirá integração segura com um serviço especializado.",14,false);button(box,"🚀 Começar meu nivelamento",v->{placement=true;question=0;results=new int[]{0,0,0};placementQuestion();});secondary(box,"Pular por enquanto",v->home());}
  private void options(LinearLayout box,String correct,ArrayList<String> distractors,Runnable done,int category){
    LinkedHashSet<String> set=new LinkedHashSet<>();set.add(correct);Collections.shuffle(distractors,random);
    for(String x:distractors){if(set.size()>=4)break;if(!x.equals(correct))set.add(x);}
    ArrayList<String> opts=new ArrayList<>(set);Collections.shuffle(opts,random);
    final boolean[] answered={false};
    for(String choice:opts)button(box,choice,v->{
      if(answered[0])return;answered[0]=true;
      if(choice.equals(correct)){
        if(placement)results[category]++;else{if(mode.equals("quiz"))quizScore++;addXp(5);}
        Toast.makeText(this,"Correto!",Toast.LENGTH_SHORT).show();done.run();
      }else{
        if(placement){Toast.makeText(this,"Resposta registrada. Correções ao final da prova.",Toast.LENGTH_SHORT).show();done.run();}
        else{rememberMistake(correct);mistake(correct,category==1?"Leia o trecho completo, observe o contexto e procure a evidência no texto.":"Reveja o significado e use a expressão em uma situação real.",done);}
      }
    });
  }
  private void placementQuestion(){mode="placement";if(question>=30){placementResult();return;}int type=question/10;int n=question%10;LinearLayout box=page("🧠 Teste inicial","Questão "+(question+1)+" de 30 • "+(type==0?"Vocabulário":type==1?"Interpretação":"Pronúncia"));progressBar(box,question,30);if(type==0){String[] item=vocab.get(n);text(box,"Escolha o significado no contexto:",18,false);picture(box,item[0]);text(box,item[0],26,true);ArrayList<String> distractors=new ArrayList<>();for(String[] row:vocab)distractors.add(row[1]);options(box,item[1],distractors,()->{question++;placementQuestion();},0);}else if(type==1){String[] item=interpretations.get(n);text(box,"Leia o texto e responda:",18,false);
    int storyIdx=n<9?n/3:3;
    if(readings.size()>storyIdx)card(box,readings.get(storyIdx).title,readings.get(storyIdx).passage);
    text(box,item[0],22,true);ArrayList<String> distractors=new ArrayList<>();for(String[] row:interpretations)distractors.add(row[1]);options(box,item[1],distractors,()->{question++;placementQuestion();},1);}else{String phrase=speech.get(n);text(box,"Ouça e repita:",18,false);text(box,phrase,24,true);feedback=text(box,"A frase precisa ser reconhecida pelo Android. Pontuação aproximada por palavras, não por sotaque.",14,false);button(box,"🔊 Ouvir frase",v->speak(phrase));button(box,"🎤 Falar e corrigir",v->listen(phrase));secondary(box,"Pular esta questão (0 ponto)",v->{question++;placementQuestion();});}}
  private void placementResult(){placement=false;getPreferences(0).edit().putBoolean("placed",true).putInt("vocab",results[0]).putInt("interpret",results[1]).putInt("speech",results[2]).apply();profile();}
  private void profile(){mode="profile";int v=getPreferences(0).getInt("vocab",0),i=getPreferences(0).getInt("interpret",0),s=getPreferences(0).getInt("speech",0);LinearLayout box=page("🏅 Seu perfil EnglishUp","Resultado do nivelamento inicial");text(box,"📚 Vocabulário: "+v+"/10 • "+level("vocab"),21,true);text(box,"🧠 Interpretação: "+i+"/10 • "+level("interpret"),21,true);text(box,"🎤 Fala reconhecida: "+s+"/10 • "+level("speech"),21,true);int min=Math.min(v,Math.min(i,s));text(box,"📍 Recomendação inicial: "+(min<5?"começar pelo básico (A1)":min<8?"reforçar fundamentos (A1–A2)":"explorar lições mais desafiadoras"),18,true);String weak=v<=i&&v<=s?"Vocabulário":i<=s?"Interpretação":"Conversação";text(box,"🤖 Plano adaptativo local: priorizar "+weak+". Essa recomendação é baseada nos seus acertos; ainda não é uma IA generativa conectada.",15,false);button(box,"🎮 Começar a estudar",x->home());secondary(box,"🔄 Refazer prova",x->welcome());}
  private int progress(String m){return getPreferences(0).getInt("progress_"+m+"_"+track,0);}
  private void save(){String key="progress_"+module+"_"+track;getPreferences(0).edit().putInt(key,Math.max(getPreferences(0).getInt(key,0),lessonIndex)).apply();}
  private void hero(LinearLayout box,String heading,String description,String icon){
    LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(20),dp(18),dp(20),dp(20));
    GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(23,69,192),Color.rgb(18,44,116),Color.rgb(12,28,75)});
    background.setCornerRadius(dp(25));hero.setBackground(background);hero.setElevation(dp(8));
    LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(12),0,dp(18));box.addView(hero,hp);
    TextView top=new TextView(this);top.setText(icon+"    ✦ ENGLISHUP ULTIMATE    🇺🇸");top.setTextSize(13);top.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));top.setTextColor(Color.rgb(225,235,255));hero.addView(top);
    TextView h=new TextView(this);h.setText(heading);h.setTextSize(26);h.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));h.setTextColor(WHITE);h.setPadding(0,dp(12),0,dp(5));hero.addView(h);
    TextView d=new TextView(this);d.setText(description);d.setTextSize(14);d.setTextColor(Color.rgb(220,230,255));hero.addView(d);
  }
  private void featurePhoto(LinearLayout box,int drawable){
    ImageView image=new ImageView(this);image.setImageResource(drawable);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setBackground(shape(Color.rgb(226,235,255),20));image.setClipToOutline(true);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(170));lp.setMargins(0,dp(8),0,dp(10));box.addView(image,lp);
    if(!reduceMotion()){image.setAlpha(0f);image.animate().alpha(1f).setDuration(350).start();}
  }
  private void home(){mode="home";LinearLayout box=page("Sua jornada ✨","Learn • Speak • Level Up");
    hero(box,"Seu inglês. Seu futuro.","Aprenda para viajar, trabalhar e estudar nos Estados Unidos.","✈️");
    featurePhoto(box,R.drawable.usa_banner);
    card(box,"✦  SEU ESPAÇO DE APRENDIZADO","⭐ "+xp()+" XP acumulados  •  5 áreas de estudo  •  3 especializações");
    secondary(box,"🔁 Revisar meus erros",v->review());
    if(getPreferences(0).getBoolean("placed",false))secondary(box,"📊 Meu perfil independente",v->profile());
    moduleTile(box,"📚 Vocabulário","500+ termos • básico, intermediário e avançado","vocab",()->choose("Vocabulário"));
    moduleTile(box,"🎤 Pronúncia americana","Ouça, repita e pratique situações reais","speech",()->choose("Conversação"));
    moduleTile(box,"📖 Interpretação de texto","Textos completos e perguntas contextualizadas","interpret",()->readingHome());
    moduleTile(box,"💬 Conversação prática","Diálogos de viagens, trabalho, estudos e cotidiano","speech",()->conversationHome());
    moduleTile(box,"🔤 Verbos","Verbos essenciais, tempos e expressões","verbs",()->choose("Verbos"));
    secondary(box,reduceMotion()?"✨ Ativar animações":"♿ Reduzir animações",v->{getPreferences(0).edit().putBoolean("reduce_motion",!reduceMotion()).apply();home();});
  }
  private void moduleTile(LinearLayout box,String title,String desc,String key,Runnable action){
    LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(16),dp(14),dp(16),dp(13));
    tile.setBackground(outlined(WHITE,Color.rgb(213,226,255),23));tile.setElevation(dp(5));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));box.addView(tile,lp);
    LinearLayout heading=new LinearLayout(this);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);tile.addView(heading);
    TextView icon=new TextView(this);icon.setText(title.substring(0,title.indexOf(' ')));icon.setTextSize(27);icon.setGravity(android.view.Gravity.CENTER);
    icon.setBackground(shape(Color.rgb(228,237,255),17));heading.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(56)));
    LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(dp(12),0,dp(6),0);heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
    text(titles,title.substring(title.indexOf(' ')+1),18,true);TextView subtitle=text(titles,desc,12,false);subtitle.setTextColor(MUTED);
    TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(30);arrow.setTextColor(Color.rgb(32,84,219));heading.addView(arrow);
    LinearLayout bottom=new LinearLayout(this);bottom.setGravity(android.view.Gravity.CENTER_VERTICAL);bottom.setPadding(dp(4),dp(7),dp(4),0);tile.addView(bottom);
    TextView level=new TextView(this);level.setText("NÍVEL "+level(key)+"  •  TOQUE PARA ENTRAR");level.setTextColor(Color.rgb(50,89,177));level.setTextSize(11);level.setTypeface(Typeface.create("sans-serif-rounded",Typeface.BOLD));bottom.addView(level);
    tile.setOnClickListener(v->{if(!reduceMotion())tile.animate().scaleX(.975f).scaleY(.975f).setDuration(85).withEndAction(()->{tile.setScaleX(1f);tile.setScaleY(1f);transition(action);}).start();else transition(action);});
  }
  private void specializationMenu(String m){module=m;mode="specialization";
    LinearLayout box=page("🎯 "+(m.equals("Conversação")?"Pronúncia americana":m),"Escolha sua trilha • do básico ao avançado");
    hero(box,"Escolha sua especialização","Do básico ao avançado, com progresso independente.","⭐");
    trackTile(box,"📘 Essencial","Básico → Intermediário → Avançado","Essencial");
    trackTile(box,"✈️ Viagens","Aeroporto, hotel, transporte e situações reais","Viagens");
    trackTile(box,"💼 Trabalho","Entrevistas, tarefas e comunicação profissional","Trabalho");
    trackTile(box,"🎓 Acadêmico","Escola, universidade e apresentações","Acadêmico");
    secondary(box,"🏠 Voltar ao menu",v->transition(()->home()));
  }
  private void trackTile(LinearLayout box,String title,String description,String name){
    LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.HORIZONTAL);tile.setGravity(android.view.Gravity.CENTER_VERTICAL);
    tile.setPadding(dp(16),dp(13),dp(14),dp(13));tile.setBackground(outlined(WHITE,Color.rgb(213,225,250),20));tile.setElevation(dp(3));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));box.addView(tile,lp);
    TextView ic=new TextView(this);ic.setText(title.substring(0,title.indexOf(' ')));ic.setTextSize(28);ic.setGravity(android.view.Gravity.CENTER);ic.setBackground(shape(Color.rgb(232,239,255),15));tile.addView(ic,new LinearLayout.LayoutParams(dp(55),dp(55)));
    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(10),0,0,0);tile.addView(body,new LinearLayout.LayoutParams(0,-2,1));
    text(body,title.substring(title.indexOf(' ')+1),18,true);TextView d=text(body,description,12,false);d.setTextColor(MUTED);
    TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextColor(Color.rgb(40,84,218));arrow.setTextSize(30);tile.addView(arrow);
    tile.setOnClickListener(v->transition(()->openTrack(name)));
  }
  private void openTrack(String selected){track=selected;
    if(module.equals("Interpretação")){readingIndex=0;readingQuestion();return;}
    active.clear();for(Lesson l:lessons){if(!l.category.equals(module))continue;
      if(selected.equals("Essencial")||l.track.equals(selected))active.add(l);}
    if(active.isEmpty()){LinearLayout box=page("Trilha em preparação","Ainda não há lições específicas suficientes nesta área.");
      text(box,"Você pode estudar a trilha essencial enquanto este conteúdo é ampliado.",16,false);
      button(box,"Ir para a trilha essencial",v->openTrack("Essencial"));secondary(box,"Voltar",v->specializationMenu(module));return;}
    String key="progress_"+module+"_"+track;
    lessonIndex=Math.min(getPreferences(0).getInt(key,0),Math.max(0,active.size()-1));moduleIntro();
  }
  private void choose(String m){specializationMenu(m);}
  private void moduleIntro(){LinearLayout box=page(module.equals("Conversação")?"🎤 American Pronunciation":module.equals("Vocabulário")?"📚 Vocabulary":"🔤 Verbos",
    module.equals("Vocabulário")?"Learn words. Use them in real life.":module.equals("Conversação")?"Listen. Speak. Improve.":"Aprenda os verbos passo a passo.");
    String key=module.equals("Vocabulário")?"vocab":module.equals("Conversação")?"speech":"verbs";
    hero(box,"Sua próxima conquista","Treinamento personalizado por habilidade e contexto.","🚀");
    levelCard(box,"Seu nível de "+module,key,"Cada habilidade evolui de forma independente.");
    card(box,"Seu treinamento","Lições curtas, exemplos reais, correções e revisão.");
    button(box,"▶ Começar treinamento",v->transition(()->lesson()));
    secondary(box,"⬅ Voltar ao menu",v->transition(()->home()));
  }
  private void lesson(){mode="lesson";if(active.isEmpty()){home();return;}Lesson l=active.get(lessonIndex);
    LinearLayout box=page("📖 "+module+" • "+(lessonIndex<50?"Básico":lessonIndex<150?"Intermediário":"Avançado"),track+" • Lição "+(lessonIndex+1)+" de "+active.size());
    progressBar(box,lessonIndex+1,active.size());
    if(module.equals("Vocabulário"))picture(box,l.en);
    text(box,l.en,27,true);
    if(module.equals("Vocabulário")){
      text(box,"Pense no significado antes de revelar. Depois, use a palavra em uma frase.",16,false);
      button(box,"💡 Revelar significado",v->{mistake(l.pt,l.tip.isEmpty()?"Use esta palavra em uma frase do cotidiano.":l.tip,()->{});});
    }else{
      text(box,"Escolha o significado sem ver a tradução antes.",16,true);
      ArrayList<String> answers=new ArrayList<>();
      for(Lesson other:active)if(!other.pt.equals(l.pt))answers.add(other.pt);
      options(box,l.pt,answers,()->{},0);
    }
    feedback=text(box,"Ouça, pratique e avance. O reconhecimento de palavras não mede o sotaque.",14,false);
    button(box,"🔊 Ouvir inglês americano",v->speak(l.en));
    button(box,"🎤 Praticar com microfone",v->listen(l.en));
    button(box,"➡ Próxima lição",v->transition(()->{if((lessonIndex+1)%10==0)startQuiz();else{lessonIndex=Math.min(lessonIndex+1,active.size()-1);save();lesson();}}));
    secondary(box,"⬅ Lição anterior",v->transition(()->{lessonIndex=Math.max(0,lessonIndex-1);lesson();}));
    secondary(box,"🏠 Menu",v->transition(()->home()));
  }
  private void readingHome(){specializationMenu("Interpretação");}
  private void readingQuestion(){mode="reading";if(readings.isEmpty()){home();return;}
    ArrayList<Reading> available=new ArrayList<>();for(Reading rr:readings)if(track.equals("Essencial")||rr.track.equals(track))available.add(rr);
    if(available.isEmpty()){LinearLayout box=page("Conteúdo em preparação","Esta trilha ainda não tem textos cadastrados.");button(box,"Voltar",v->specializationMenu("Interpretação"));return;}
    int story=Math.min(readingIndex/3,available.size()-1),q=readingIndex%3;
    if(readingIndex>=available.size()*3){LinearLayout box=page("🎉 Leitura concluída","Você completou as atividades de interpretação.");
      button(box,"Voltar ao menu",v->home());return;}
    Reading r=available.get(story);String[] item=r.qs.get(q);
    LinearLayout box=page("📖 "+r.title,"Texto "+(story+1)+" • Pergunta "+(q+1)+" de 3");
    progressBar(box,readingIndex+1,available.size()*3);
    card(box,"READ THE TEXT",r.passage);
    text(box,item[0],20,true);
    ArrayList<String> opts=new ArrayList<>(Arrays.asList(item[2],item[3],item[4]));
    options(box,item[1],opts,()->{readingIndex++;readingQuestion();},1);
    secondary(box,"🏠 Menu",v->home());
  }
  private void startQuiz(){quizIndex=0;quizScore=0;quiz();}
  private void quiz(){mode="quiz";int start=(lessonIndex/10)*10,count=Math.min(10,active.size()-start);if(quizIndex>=count){quizResult(count);return;}Lesson l=active.get(start+quizIndex);LinearLayout box=page("🏆 Desafio do nível","Questão "+(quizIndex+1)+" de "+count);
    text(box,"Explique o significado em português:",17,false);text(box,l.en,24,true);
    EditText typed=new EditText(this);typed.setSingleLine(false);typed.setMinLines(2);typed.setHint("Escreva sua resposta sem alternativas...");box.addView(typed);
    button(box,"Conferir minha resposta",v->{String answer=typed.getText().toString().trim();if(answer.isEmpty()){Toast.makeText(this,"Escreva sua resposta primeiro.",Toast.LENGTH_SHORT).show();return;}
      int judgment=meaningMatch(l.en,l.pt,answer);
      if(judgment==2){quizScore++;addXp(5);}
      if(judgment==0)rememberMistake(l.en+" = "+l.pt);
      String message="Resposta de referência: "+l.pt+"\nSua resposta: "+answer;
      if(judgment==1)message+="\n\nNão reconheci essa formulação automaticamente. Se sua resposta tem o mesmo sentido, marque como válida.";
      android.app.AlertDialog dlg=new android.app.AlertDialog.Builder(this).create();
      LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(19),dp(18),dp(19),dp(18));panel.setBackground(shape(Color.rgb(242,247,255),23));
      text(panel,judgment==2?"🎉 Muito bem!":"✨ Correção inteligente",24,true);
      card(panel,"🎯 Resposta de referência",l.pt);
      card(panel,"✍️ Sua resposta",answer);
      text(panel,judgment==1?"A resposta não corresponde exatamente às expressões cadastradas. Confira o sentido antes de decidir.":"O importante é compreender a ideia, não decorar uma única frase.",14,false);
      button(panel,judgment==1?"✓ Minha resposta tem o mesmo sentido":"Próxima questão →",w->{dlg.dismiss();if(judgment==1){quizScore++;addXp(5);}quizIndex++;quiz();});
      if(judgment==1)secondary(panel,"↻ Preciso revisar",w->{dlg.dismiss();rememberMistake(l.en+" = "+l.pt);quizIndex++;quiz();});
      dlg.setView(panel);dlg.setCancelable(false);dlg.show();
    });}
  private void quizResult(int total){LinearLayout box=page("🏁 Resultado","Você acertou "+quizScore+" de "+total);if(quizScore>=Math.ceil(total*.8)){addXp(30);text(box,"🎉 Nível liberado! +30 XP",22,true);boolean finished=(lessonIndex/10+1)*10>=active.size();lessonIndex=Math.min((lessonIndex/10+1)*10,active.size()-1);save();button(box,finished?"Concluir trilha":"Continuar",v->{if(finished)home();else lesson();});}else{text(box,"Revise e tente novamente. Precisa de 80%.",18,false);button(box,"Revisar",v->{lessonIndex=(lessonIndex/10)*10;lesson();});button(box,"Refazer prova",v->startQuiz());}button(box,"Menu",v->home());}
  @Override public void onBackPressed(){if(mode.equals("home"))super.onBackPressed();else home();}
  @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
