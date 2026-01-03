package com.draw.perfect;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private DrawView drawView;
    private TextView roundText, totalScoreText;
    private String[] shapes = {"Circle", "Square", "Triangle", "Star"};
    private int currentRound = 1;
    private int totalRounds = 5;
    private int totalScore = 0;
    private Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        drawView = findViewById(R.id.drawView);
        roundText = findViewById(R.id.roundText);
        totalScoreText = findViewById(R.id.totalScoreText);

        startRound();

        drawView.setOnDrawCompleteListener(accuracy -> {
            totalScore += accuracy;
            Toast.makeText(this, "Round " + currentRound + " Accuracy: " + accuracy + "%", Toast.LENGTH_SHORT).show();
            if (currentRound < totalRounds) {
                currentRound++;
                startRound();
            } else {
                Toast.makeText(this, "Game Over! Total Score: " + totalScore, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startRound() {
        roundText.setText("Round: " + currentRound + "/" + totalRounds);
        totalScoreText.setText("Score: " + totalScore);

        String nextShape = shapes[random.nextInt(shapes.length)];
        drawView.setTargetShape(nextShape);
        drawView.setDrawColor(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
    }
}