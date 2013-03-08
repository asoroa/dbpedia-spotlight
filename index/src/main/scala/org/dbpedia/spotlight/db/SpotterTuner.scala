package org.dbpedia.spotlight.db

import model.Tokenizer
import breeze.linalg.{DenseMatrix, DenseVector}
import org.dbpedia.spotlight.io.AnnotatedTextSource
import breeze.regress.LinearRegression
import java.io.File
import org.dbpedia.spotlight.model.{SurfaceForm, SurfaceFormOccurrence, AnnotatedParagraph}
import org.apache.commons.io.FileUtils
import scala.collection.JavaConversions._


object SpotterTuner {

  def tuneOpenNLP(corpus: AnnotatedTextSource, tokenizer: Tokenizer, spotter: DBSpotter, outputFile: File) {

    System.err.println("Tuning Spotter model...")

    val activeCorpus = corpus.take(20000)

    val allSpots = activeCorpus.map{ par: AnnotatedParagraph => {
      tokenizer.tokenizeMaybe(par.text)

      /* We are supposing the case that the tuning material is
         from Wikipedia. Therefore usually only the first link
         is annotated, but all links with the same name in the
         article should be annotated. For this reason, we use
         only the sf string to identify a spot.
      */
      (par.occurrences.map(_.surfaceForm.name).toSet, spotter.extract(par.text))
    }}


    val (nx, ny) = (DBSpotter.spotFeatures(new SurfaceForm("test")).activeSize, allSpots.map(_._2.size()).sum)

    val x = DenseMatrix.zeros[Double](ny, nx)
    val y = DenseVector.zeros[Double](ny)

    var i = 0
    allSpots.foreach{
      case(goldSpotSet: Set[String], spots: java.util.List[SurfaceFormOccurrence]) => {
        spots.foreach{ spot: SurfaceFormOccurrence =>
          x(i,::) := DBSpotter.spotFeatures(spot.surfaceForm).t
          y(i)     = ( if(goldSpotSet.contains(spot.surfaceForm.name)) 1.0 else 0.0 )
          i += 1
        }
      }

    }

    //System.err.println(x)

    FileUtils.write(
      outputFile,
      LinearRegression.regress(x, y).activeValuesIterator.mkString(" ")
    )

  }

}