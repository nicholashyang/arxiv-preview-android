package com.example.arxivpreview.model

data class ArxivCategory(
    val code: String,
    val name: String,
    val group: String,
)

object ArxivCategories {
    val all: List<ArxivCategory> = buildList {
        group("Computer Science", "cs", listOf(
            "AI" to "Artificial Intelligence", "AR" to "Hardware Architecture",
            "CC" to "Computational Complexity", "CE" to "Computational Engineering",
            "CG" to "Computational Geometry", "CL" to "Computation and Language",
            "CR" to "Cryptography and Security", "CV" to "Computer Vision",
            "CY" to "Computers and Society", "DB" to "Databases",
            "DC" to "Distributed Computing", "DL" to "Digital Libraries",
            "DM" to "Discrete Mathematics", "DS" to "Data Structures and Algorithms",
            "ET" to "Emerging Technologies", "FL" to "Formal Languages",
            "GL" to "General Literature", "GR" to "Graphics",
            "GT" to "Computer Science and Game Theory", "HC" to "Human-Computer Interaction",
            "IR" to "Information Retrieval", "IT" to "Information Theory",
            "LG" to "Machine Learning", "LO" to "Logic in Computer Science",
            "MA" to "Multiagent Systems", "MM" to "Multimedia",
            "MS" to "Mathematical Software", "NA" to "Numerical Analysis",
            "NE" to "Neural and Evolutionary Computing", "NI" to "Networking",
            "OH" to "Other Computer Science", "OS" to "Operating Systems",
            "PF" to "Performance", "PL" to "Programming Languages",
            "RO" to "Robotics", "SC" to "Symbolic Computation",
            "SD" to "Sound", "SE" to "Software Engineering",
            "SI" to "Social and Information Networks", "SY" to "Systems and Control",
        ))
        group("Mathematics", "math", listOf(
            "AC" to "Commutative Algebra", "AG" to "Algebraic Geometry",
            "AP" to "Analysis of PDEs", "AT" to "Algebraic Topology",
            "CA" to "Classical Analysis", "CO" to "Combinatorics",
            "CT" to "Category Theory", "CV" to "Complex Variables",
            "DG" to "Differential Geometry", "DS" to "Dynamical Systems",
            "FA" to "Functional Analysis", "GM" to "General Mathematics",
            "GN" to "General Topology", "GR" to "Group Theory",
            "GT" to "Geometric Topology", "HO" to "History and Overview",
            "IT" to "Information Theory", "KT" to "K-Theory and Homology",
            "LO" to "Logic", "MG" to "Metric Geometry",
            "MP" to "Mathematical Physics", "NA" to "Numerical Analysis",
            "NT" to "Number Theory", "OA" to "Operator Algebras",
            "OC" to "Optimization and Control", "PR" to "Probability",
            "QA" to "Quantum Algebra", "RA" to "Rings and Algebras",
            "RT" to "Representation Theory", "SG" to "Symplectic Geometry",
            "SP" to "Spectral Theory", "ST" to "Statistics Theory",
        ))
        group("Statistics", "stat", listOf(
            "AP" to "Applications", "CO" to "Computation", "ME" to "Methodology",
            "ML" to "Machine Learning", "OT" to "Other Statistics", "TH" to "Theory",
        ))
        group("Electrical Engineering and Systems Science", "eess", listOf(
            "AS" to "Audio and Speech Processing", "IV" to "Image and Video Processing",
            "SP" to "Signal Processing", "SY" to "Systems and Control",
        ))
        group("Economics", "econ", listOf(
            "EM" to "Econometrics", "GN" to "General Economics", "TH" to "Theoretical Economics",
        ))
        group("Quantitative Biology", "q-bio", listOf(
            "BM" to "Biomolecules", "CB" to "Cell Behavior", "GN" to "Genomics",
            "MN" to "Molecular Networks", "NC" to "Neurons and Cognition",
            "OT" to "Other Quantitative Biology", "PE" to "Populations and Evolution",
            "QM" to "Quantitative Methods", "SC" to "Subcellular Processes",
            "TO" to "Tissues and Organs",
        ))
        group("Quantitative Finance", "q-fin", listOf(
            "CP" to "Computational Finance", "EC" to "Economics",
            "GN" to "General Finance", "MF" to "Mathematical Finance",
            "PM" to "Portfolio Management", "PR" to "Pricing of Securities",
            "RM" to "Risk Management", "ST" to "Statistical Finance",
            "TR" to "Trading and Market Microstructure",
        ))
        group("Physics", "astro-ph", listOf(
            "CO" to "Cosmology and Nongalactic Astrophysics",
            "EP" to "Earth and Planetary Astrophysics", "GA" to "Astrophysics of Galaxies",
            "HE" to "High Energy Astrophysical Phenomena",
            "IM" to "Instrumentation and Methods for Astrophysics",
            "SR" to "Solar and Stellar Astrophysics",
        ))
        group("Physics", "cond-mat", listOf(
            "dis-nn" to "Disordered Systems", "mes-hall" to "Mesoscale and Nanoscale Physics",
            "mtrl-sci" to "Materials Science", "other" to "Other Condensed Matter",
            "quant-gas" to "Quantum Gases", "soft" to "Soft Condensed Matter",
            "stat-mech" to "Statistical Mechanics", "str-el" to "Strongly Correlated Electrons",
            "supr-con" to "Superconductivity",
        ))
        group("Physics", "physics", listOf(
            "acc-ph" to "Accelerator Physics", "ao-ph" to "Atmospheric and Oceanic Physics",
            "app-ph" to "Applied Physics", "atom-ph" to "Atomic Physics",
            "bio-ph" to "Biological Physics", "chem-ph" to "Chemical Physics",
            "class-ph" to "Classical Physics", "comp-ph" to "Computational Physics",
            "data-an" to "Data Analysis", "ed-ph" to "Physics Education",
            "flu-dyn" to "Fluid Dynamics", "gen-ph" to "General Physics",
            "geo-ph" to "Geophysics", "hist-ph" to "History and Philosophy of Physics",
            "ins-det" to "Instrumentation and Detectors", "med-ph" to "Medical Physics",
            "optics" to "Optics", "plasm-ph" to "Plasma Physics",
            "pop-ph" to "Popular Physics", "soc-ph" to "Physics and Society",
            "space-ph" to "Space Physics",
        ))
        addSingles("Physics", listOf(
            "gr-qc" to "General Relativity and Quantum Cosmology",
            "hep-ex" to "High Energy Physics - Experiment",
            "hep-lat" to "High Energy Physics - Lattice",
            "hep-ph" to "High Energy Physics - Phenomenology",
            "hep-th" to "High Energy Physics - Theory",
            "math-ph" to "Mathematical Physics",
            "nucl-ex" to "Nuclear Experiment", "nucl-th" to "Nuclear Theory",
            "quant-ph" to "Quantum Physics",
        ))
        group("Nonlinear Sciences", "nlin", listOf(
            "AO" to "Adaptation and Self-Organizing Systems",
            "CD" to "Chaotic Dynamics", "CG" to "Cellular Automata and Lattice Gases",
            "PS" to "Pattern Formation and Solitons",
            "SI" to "Exactly Solvable and Integrable Systems",
        ))
    }.sortedWith(compareBy({ it.group }, { it.code }))

    private fun MutableList<ArxivCategory>.group(
        group: String,
        prefix: String,
        entries: List<Pair<String, String>>,
    ) {
        entries.forEach { (suffix, name) -> add(ArxivCategory("$prefix.$suffix", name, group)) }
    }

    private fun MutableList<ArxivCategory>.addSingles(
        group: String,
        entries: List<Pair<String, String>>,
    ) {
        entries.forEach { (code, name) -> add(ArxivCategory(code, name, group)) }
    }
}
