/*
 * Copyright (c) 2026 Starbright Lab.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

/**
 * Bundled public-domain children's stories for the bedtime tile. All are classic fables
 * (Aesop) and traditional tales, long out of copyright, so they can ship in an
 * open-source APK. Kept short and gentle — they're read aloud via Android TTS while the
 * text shows big on screen, pairing the Portal's screen + speaker for a child's room.
 */
object Stories {

  data class Story(val title: String, val emoji: String, val paragraphs: List<String>)

  val ALL =
      listOf(
          Story(
              "The Tortoise and the Hare",
              "🐢",
              listOf(
                  "A speedy hare once made fun of a slow-moving tortoise. Tired of the teasing, the tortoise said, \"Let us have a race.\" The hare laughed, but agreed.",
                  "The fox set them off. The hare dashed almost out of sight at once, then stopped. To show how little he thought of the tortoise, he lay down beside the path to nap.",
                  "The tortoise walked, and walked, and walked. He never stopped, not once, until he reached the finish line.",
                  "The hare woke with a start and ran his hardest — but the tortoise had already won. \"Slow and steady,\" the tortoise smiled, \"wins the race.\"",
              )),
          Story(
              "The Lion and the Mouse",
              "🦁",
              listOf(
                  "A great lion lay asleep in the sun. A little mouse ran across his nose and woke him. The lion caught the mouse in his huge paw.",
                  "\"Please let me go,\" squeaked the mouse, \"and one day I may help you.\" The lion laughed at the thought, but he was kind, and let the little mouse go.",
                  "Not long after, hunters caught the lion in a strong net. He roared and roared, but he could not get free.",
                  "The little mouse heard him. She came and gnawed the ropes, one by one, until the lion was free. \"You see,\" said the mouse, \"even a little friend can be a great friend.\"",
              )),
          Story(
              "The Little Red Hen",
              "🐔",
              listOf(
                  "A little red hen found some grains of wheat. \"Who will help me plant the wheat?\" she asked. \"Not I,\" said the cat, the dog, and the pig. \"Then I will,\" said the hen. And she did.",
                  "When the wheat was tall, she asked, \"Who will help me cut it?\" \"Not I,\" they all said. \"Then I will,\" said the hen. And she did.",
                  "She asked who would help carry it to the mill, and bake the bread. Each time they said, \"Not I.\" Each time the hen said, \"Then I will.\" And she did.",
                  "At last the warm bread was ready. \"Who will help me eat it?\" \"I will!\" they all cried. \"No,\" said the little red hen. \"I planted, and cut, and baked it myself. And I will eat it myself.\" And she did.",
              )),
          Story(
              "The North Wind and the Sun",
              "☀️",
              listOf(
                  "The North Wind and the Sun argued about who was stronger. They saw a traveler walking along, and agreed: whoever could make him take off his coat was the stronger.",
                  "The North Wind blew first. He blew cold and hard. But the harder he blew, the tighter the traveler wrapped his coat around himself.",
                  "Then it was the Sun's turn. The Sun shone gently, warmer and warmer. Soon the traveler grew so warm that he took his coat right off.",
                  "\"Gentleness,\" said the Sun, \"can do what force cannot.\"",
              )),
          Story(
              "The Ant and the Grasshopper",
              "🐜",
              listOf(
                  "All summer long the grasshopper sang and played, while the ants worked hard, carrying food to store away.",
                  "\"Why work so hard?\" laughed the grasshopper. \"Come and sing with me!\" But the ants kept working, gathering grain for the winter.",
                  "When the cold winter came, the grasshopper had nothing to eat. The ants, warm in their nest, had plenty.",
                  "The grasshopper learned that there is a time to play and a time to work — and it is wise to make ready while you can.",
              )),
      )
}
